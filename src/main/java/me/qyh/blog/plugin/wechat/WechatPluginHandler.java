package me.qyh.blog.plugin.wechat;

import java.lang.reflect.Method;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import me.qyh.blog.core.config.UrlHelper;
import me.qyh.blog.core.exception.SystemException;
import me.qyh.blog.core.util.FileUtils;
import me.qyh.blog.core.util.Validators;
import me.qyh.blog.plugin.PluginHandlerAdapter;
import me.qyh.blog.plugin.PluginProperties;
import me.qyh.blog.plugin.RequestMappingRegistry;
import me.qyh.blog.plugin.wechat.WechatSupport.Signature;

public class WechatPluginHandler extends PluginHandlerAdapter {

	private WechatSupport wechatSupport;

	@Autowired
	private UrlHelper urlHelper;

	private static final String APPID_KEY = "plugin.wechat.appid";
	private static final String APPSECRET_KEY = "plugin.wechat.appsecret";

	@Override
	public void init(ApplicationContext applicationContext) {
		Map<String, String> map = PluginProperties.getInstance().gets(APPID_KEY, APPSECRET_KEY);
		String appid = map.get(APPID_KEY);
		String appsecret = map.get(APPSECRET_KEY);
		if (!Validators.isEmptyOrNull(appid, true) && !Validators.isEmptyOrNull(appsecret, true)) {
			wechatSupport = new WechatSupport(appid.trim(), appsecret.trim());
		}
	}

	@Override
	public void addRequestHandlerMapping(RequestMappingRegistry registry) {
		if (wechatSupport == null) {
			return;
		}
		Method method = getDeclaredMethod(WechatController.class, "jsConfig", String.class);
		registry.register(RequestMappingInfo.paths("wechat/jsConfig").methods(RequestMethod.GET),
				new WechatController(), method);
	}

	private Method getDeclaredMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
		try {
			return clazz.getDeclaredMethod(name, parameterTypes);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new SystemException(e.getMessage(), e);
		}
	}

	public class WechatController {

		@ResponseBody
		public Signature jsConfig(@RequestParam(name = "path", defaultValue = "") String path) {
			String clean = FileUtils.cleanPath(path);
			String url = clean.isEmpty() ? urlHelper.getUrl() : urlHelper.getUrl() + '/' + clean;
			return wechatSupport.createSignature(url);
		}

	}

}