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
package me.qyh.blog.template.validator;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import me.qyh.blog.core.util.Validators;
import me.qyh.blog.template.vo.TemplatePageQueryParam;

@Component
public class TemplatePageQueryParamValidator implements Validator {

	private static final int MAX_NAME_LENGTH = 20;
	private static final int MAX_ALIAS_LENGTH = 255;

	@Override
	public boolean supports(Class<?> clazz) {
		return TemplatePageQueryParam.class.isAssignableFrom(clazz);
	}

	@Override
	public void validate(Object target, Errors errors) {
		TemplatePageQueryParam param = (TemplatePageQueryParam) target;
		if (param.getCurrentPage() < 1) {
			param.setCurrentPage(1);
		}
		String name = param.getName();
		if (!Validators.isEmptyOrNull(name, true) && param.getName().length() > MAX_NAME_LENGTH) {
			param.setName(param.getName().substring(0, MAX_NAME_LENGTH));
		}

		String alias = param.getAlias();
		if (!Validators.isEmptyOrNull(alias, true) && param.getAlias().length() > MAX_ALIAS_LENGTH) {
			param.setAlias(param.getAlias().substring(0, MAX_ALIAS_LENGTH));
		}
	}
}
