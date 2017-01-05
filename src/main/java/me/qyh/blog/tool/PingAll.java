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
package me.qyh.blog.tool;

import java.util.List;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import me.qyh.blog.dao.ArticleDao;
import me.qyh.blog.entity.Article;
import me.qyh.blog.evt.ArticleEvent;
import me.qyh.blog.evt.ArticleEvent.EventType;
import me.qyh.blog.service.ArticleService;

/**
 * ping所有<b>已经发布</b>的文章
 * 
 * @author Administrator
 *
 */
public class PingAll {

	public static void main(String[] args) {
		try (ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(
				"resources/spring/applicationContext.xml")) {
			ArticleDao articleDao = ctx.getBean(ArticleDao.class);
			List<Article> articles = articleDao.selectPublished(null);
			// async
			ctx.publishEvent(new ArticleEvent(ctx.getBean(ArticleService.class), articles, EventType.UPDATE));
		}
	}

}