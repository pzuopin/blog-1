/*
 * Copyright 2016 qyh.me
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.qyh.blog.plugin.cachehits;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;

import me.qyh.blog.core.context.Environment;
import me.qyh.blog.core.dao.ArticleDao;
import me.qyh.blog.core.entity.Article;
import me.qyh.blog.core.event.ArticleDelEvent;
import me.qyh.blog.core.exception.SystemException;
import me.qyh.blog.core.service.impl.ArticleIndexer;
import me.qyh.blog.core.service.impl.HitsStrategy;
import me.qyh.blog.core.service.impl.Transactions;

/**
 * 将点击数缓存起来，每隔一定的时间刷入数据库
 * <p>
 * <b>由于缓存原因，根据点击量查询无法实时的反应当前结果</b>
 * </p>
 * 
 * @author Administrator
 *
 */
public final class CacheableHitsStrategy implements HitsStrategy, InitializingBean {

	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private ArticleIndexer articleIndexer;
	@Autowired
	private SqlSessionFactory sqlSessionFactory;
	@Autowired
	private TaskScheduler taskScheduler;
	/**
	 * 存储所有文章的点击数
	 */
	private final Map<Integer, HitsHandler> hitsMap = new ConcurrentHashMap<>();

	/**
	 * 储存待刷新点击数的文章
	 */
	private final Map<Integer, Boolean> flushMap = new ConcurrentHashMap<>();

	/**
	 * 如果该项为true，那么在flush之前，相同的ip点击只算一次点击量
	 * <p>
	 * 例如我点击一次增加了一次点击量，一分钟后flush，那么我在这一分钟内(ip的不变的情况下)，无论我点击了多少次，都只算一次
	 * </p>
	 */
	private final boolean cacheIp;

	/**
	 * 最多保存的ip数，如果达到或超过该数目，将会立即更新
	 */
	private final int maxIps;

	/**
	 * 每50条写入数据库
	 */
	private final int flushNum;

	private final int flushSec;

	public CacheableHitsStrategy(boolean cacheIp, int maxIps, int flushNum, int flushSec) {
		if (maxIps < 0) {
			throw new SystemException("maxIps不能小于0");
		}
		if (flushNum < 0) {
			throw new SystemException("flushNum不能小于0");
		}
		if (flushSec < 0) {
			throw new SystemException("flushSec不能小于0");
		}
		this.maxIps = maxIps;
		this.flushNum = flushNum;
		this.cacheIp = cacheIp;
		this.flushSec = flushSec;
	}

	@Override
	public void hit(Article article) {
		// increase
		hitsMap.computeIfAbsent(article.getId(), k -> cacheIp ? new IPBasedHitsHandler(article.getHits(), maxIps)
				: new DefaultHitsHandler(article.getHits())).hit(article);
		flushMap.putIfAbsent(article.getId(), Boolean.TRUE);
	}

	private synchronized void doFlush(List<HitsWrapper> wrappers, boolean contextClose) {
		// 得到当前的实时点击数
		Map<Integer, Integer> hitsMap = wrappers.stream().filter(wrapper -> wrapper.hitsHandler != null)
				.collect(Collectors.toMap(wrapper -> wrapper.id, wrapper -> wrapper.hitsHandler.getHits()));
		if (!hitsMap.isEmpty()) {

			Transactions.executeInTransaction(transactionManager, status -> {
				if (!contextClose) {
					Transactions.afterCommit(() -> {
						int num = 0;
						List<Integer> ids = new ArrayList<>();
						for (Integer id : hitsMap.keySet()) {
							ids.add(id);
							if (++num % flushNum == 0) {
								articleIndexer.addOrUpdateDocument(ids.toArray(new Integer[ids.size()]));
								ids.clear();
							}
						}
						articleIndexer.addOrUpdateDocument(ids.toArray(new Integer[ids.size()]));
					});
				}

				try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
					ArticleDao articleDao = sqlSession.getMapper(ArticleDao.class);
					int num = 0;
					for (Map.Entry<Integer, Integer> it : hitsMap.entrySet()) {
						articleDao.updateHits(it.getKey(), it.getValue());
						num++;
						if (num % flushNum == 0) {
							sqlSession.commit();
						}
					}
					sqlSession.commit();
				}

			});
		}
	}

	private final class HitsWrapper {
		private final Integer id;
		private final HitsHandler hitsHandler;

		HitsWrapper(Integer id, HitsHandler hitsHandler) {
			super();
			this.id = id;
			this.hitsHandler = hitsHandler;
		}
	}

	private interface HitsHandler {
		void hit(Article article);

		int getHits();
	}

	private final class DefaultHitsHandler implements HitsHandler {

		private final LongAdder adder;

		private DefaultHitsHandler(int init) {
			adder = new LongAdder();
			adder.add(init);
		}

		@Override
		public void hit(Article article) {
			adder.increment();
		}

		@Override
		public int getHits() {
			return adder.intValue();
		}
	}

	private final class IPBasedHitsHandler implements HitsHandler {
		private final Map<String, Boolean> ips = new ConcurrentHashMap<>();
		private final LongAdder adder;
		private final int maxIps;
		private final AtomicInteger counter = new AtomicInteger(0);

		private IPBasedHitsHandler(int init, int maxIps) {
			adder = new LongAdder();
			adder.add(init);
			this.maxIps = maxIps;
		}

		@Override
		public void hit(Article article) {
			String ip = Environment.getIP();
			if (ip != null && ips.putIfAbsent(ip, Boolean.TRUE) == null) {
				adder.increment();
				if (counter.incrementAndGet() >= maxIps) {
					Integer id = article.getId();
					if (flushMap.remove(id) != null) {
						doFlush(List.of(new HitsWrapper(id, hitsMap.get(id))), false);
					}
				}
			}
		}

		@Override
		public int getHits() {
			return adder.intValue();
		}
	}

	private void flush() {
		flush(false);
	}

	private void flush(boolean contextClose) {
		if (!flushMap.isEmpty()) {
			List<HitsWrapper> wrappers = new ArrayList<>();
			for (Iterator<Entry<Integer, Boolean>> iter = flushMap.entrySet().iterator(); iter.hasNext();) {
				Entry<Integer, Boolean> entry = iter.next();
				Integer key = entry.getKey();

				if (flushMap.remove(key) != null) {
					wrappers.add(new HitsWrapper(key, hitsMap.get(key)));
				}
			}
			doFlush(wrappers, contextClose);
		}
	}

	@EventListener
	public void handleContextEvent(ContextClosedEvent event) {
		if (event.getApplicationContext().getParent() != null) {
			return;
		}
		flush(true);
	}

	@TransactionalEventListener
	public void handleArticleEvent(ArticleDelEvent evt) {
		if (!evt.isLogicDelete()) {
			evt.getArticles().stream().map(Article::getId).forEach(id -> {
				flushMap.remove(id);
				hitsMap.remove(id);
			});
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		taskScheduler.scheduleAtFixedRate(this::flush, flushSec * 1000L);
	}
}