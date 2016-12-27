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
package me.qyh.blog.web.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import me.qyh.blog.bean.JsonResult;
import me.qyh.blog.entity.Article;
import me.qyh.blog.entity.Article.ArticleStatus;
import me.qyh.blog.entity.Editor;
import me.qyh.blog.entity.Space;
import me.qyh.blog.exception.LogicException;
import me.qyh.blog.message.Message;
import me.qyh.blog.pageparam.ArticleQueryParam;
import me.qyh.blog.pageparam.SpaceQueryParam;
import me.qyh.blog.service.ArticleService;
import me.qyh.blog.service.SpaceService;
import me.qyh.blog.service.impl.Markdown2Html;
import me.qyh.blog.web.controller.form.ArticleQueryParamValidator;
import me.qyh.blog.web.controller.form.ArticleValidator;

/**
 * 、 文章管理控制器
 * 
 * @author Administrator
 *
 */
@Controller
@RequestMapping("mgr/article")
public class ArticleMgrController extends BaseMgrController {

	@Autowired
	private SpaceService spaceService;
	@Autowired
	private ArticleService articleService;
	@Autowired
	private ArticleValidator articleValidator;
	@Autowired
	private ArticleQueryParamValidator articleQueryParamValidator;
	@Autowired
	private Markdown2Html markdown2Html;

	@InitBinder(value = "article")
	protected void initBinder(WebDataBinder binder) {
		binder.setValidator(articleValidator);
	}

	@InitBinder(value = "articleQueryParam")
	protected void initQueryBinder(WebDataBinder binder) {
		binder.setValidator(articleQueryParamValidator);
	}

	@RequestMapping("index")
	public String index(@Validated ArticleQueryParam articleQueryParam, BindingResult br, Model model) {
		if (br.hasErrors()) {
			articleQueryParam = new ArticleQueryParam();
			articleQueryParam.setCurrentPage(1);
		}
		if (articleQueryParam.getStatus() == null) {
			articleQueryParam.setStatus(ArticleStatus.PUBLISHED);
		}
		articleQueryParam.setQueryPrivate(true);
		model.addAttribute("page", articleService.queryArticle(articleQueryParam));
		return "mgr/article/index";
	}

	@RequestMapping("logicDelete")
	@ResponseBody
	public JsonResult logicDelete(@RequestParam("id") Integer id) throws LogicException {
		articleService.logicDeleteArticle(id);
		return new JsonResult(true, new Message("article.logicDelete.success", "放入回收站成功"));
	}

	@RequestMapping("recover")
	@ResponseBody
	public JsonResult recover(@RequestParam("id") Integer id) throws LogicException {
		articleService.recoverArticle(id);
		return new JsonResult(true, new Message("article.recover.success", "恢复成功"));
	}

	@RequestMapping("delete")
	@ResponseBody
	public JsonResult delete(@RequestParam("id") Integer id) throws LogicException {
		articleService.deleteArticle(id);
		return new JsonResult(true, new Message("article.delete.success", "删除成功"));
	}

	@RequestMapping(value = "write", method = RequestMethod.GET)
	public String write(@RequestParam(value = "editor", required = false, defaultValue = "MD") Editor editor,
			RedirectAttributes ra, Model model) {
		SpaceQueryParam param = new SpaceQueryParam();
		List<Space> spaces = spaceService.querySpace(param);
		if (spaces.isEmpty()) {
			// 没有任何可用空间，跳转到空间管理页面
			ra.addFlashAttribute(ERROR, new Message("artic.write.noSpace", "在撰写文章之前，应该首先创建一个可用的空间"));
			return "redirect:/mgr/space/index";
		}
		model.addAttribute("spaces", spaces);
		model.addAttribute("article", new Article());
		switch (editor) {
		case MD:
			return "mgr/article/write/md";
		default:
			return "mgr/article/write/html";
		}
	}

	@RequestMapping(value = "write/md/preview", method = RequestMethod.GET)
	public String mdPreview() {
		return "mgr/article/write/mdpreview";
	}

	@RequestMapping(value = "write/md/preview", method = RequestMethod.POST)
	@ResponseBody
	public JsonResult mdPreview(@RequestParam("content") String markdown) {
		return new JsonResult(true, markdown2Html.toHtml(markdown));
	}

	@RequestMapping(value = "write", method = RequestMethod.POST)
	@ResponseBody
	public JsonResult write(@RequestBody @Validated Article article,
			@RequestParam(value = "autoDraft") boolean autoDraft) throws LogicException {
		return new JsonResult(true, articleService.writeArticle(article, autoDraft));
	}

	@RequestMapping(value = "pub", method = RequestMethod.POST)
	@ResponseBody
	public JsonResult pub(@RequestParam("id") Integer id) throws LogicException {
		articleService.publishDraft(id);
		return new JsonResult(true, "article.pub.success");
	}

	@RequestMapping(value = "update/{id}", method = RequestMethod.GET)
	public String update(@PathVariable("id") Integer id, RedirectAttributes ra, Model model) {
		Article article;
		try {
			article = articleService.getArticleForEdit(id);
		} catch (LogicException e) {
			ra.addFlashAttribute(ERROR, new Message("article.notExists", "文章不存在"));
			return "redirect:/mgr/article/index";
		}
		model.addAttribute("article", article);
		model.addAttribute("spaces", spaceService.querySpace(new SpaceQueryParam()));
		return "mgr/article/write/" + article.getEditor().name().toLowerCase();
	}

}
