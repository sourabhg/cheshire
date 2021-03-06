/**
 * 
 */
package com.trendrr.cheshire.authentication;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.trendrr.cheshire.CheshireController;
import com.trendrr.cheshire.caching.TrendrrCaches;
import com.trendrr.oss.DynMap;
import com.trendrr.oss.Reflection;
import com.trendrr.oss.Timeframe;
import com.trendrr.oss.cache.TrendrrCache;
import com.trendrr.oss.cache.TrendrrCacheItem;
import com.trendrr.strest.server.StrestController;


/**
 * @author Dustin Norlander
 * @created Jan 30, 2012
 * 
 */
public abstract class DefaultAuthenticationProvider implements AuthenticationProvider {

	protected static Logger log = LoggerFactory.getLogger(DefaultAuthenticationProvider.class);

	
	public TrendrrCache getCache(CheshireController controller) {
		return TrendrrCaches.getCacheOrDefault("authentication.cache", controller);
	}
	
	/**
	 * Gets from the cache. Key should be something unique to this login (the auth token, or sha1(username+password), ect
	 * @param cache
	 * @param key
	 * @return
	 */
	public AuthToken getFromCache(CheshireController controller, String key) {
		try {
			Object obj = this.getCache(controller).get("auth_tokens", key);
			if (obj == null)
				return null;
			TrendrrCacheItem item = TrendrrCacheItem.deserialize((byte[])obj);
			String cls = item.getMetadata().getString("auth_token_class");
			DynMap content = DynMap.instance(new String(item.getContentBytes(), "utf8"));
			AuthToken tok = AuthToken.instance(cls, content);
			log.info("got auth from cache: " + content.toJSONString());
			return tok;
		} catch (Exception e) {
			log.error("caught", e);
		}
		return null;
	}
	
	public void saveToCache(CheshireController controller, String key, AuthToken token, int timeoutSeconds) {
		TrendrrCacheItem item = new TrendrrCacheItem();
		item.getMetadata().put("auth_token_class", token.getClass().getName());
		try {
			item.setContentBytes(token.toDynMap().toJSONString().getBytes("utf8"));
			this.getCache(controller).set("auth_tokens", key, item.serialize(), Timeframe.SECONDS.add(new Date(), timeoutSeconds));
		} catch (Exception e) {
			log.error("caught", e);
		}	
	}
}
