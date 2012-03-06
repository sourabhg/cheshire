/**
 * 
 */
package com.trendrr.cheshire.filters;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponse;

import com.trendrr.cheshire.CheshireController;
import com.trendrr.cheshire.CheshireController;
import com.trendrr.cheshire.caching.TrendrrCaches;
import com.trendrr.oss.DynMap;
import com.trendrr.oss.Timeframe;
import com.trendrr.oss.cache.TrendrrCache;
import com.trendrr.oss.cache.TrendrrCacheItem;
import com.trendrr.oss.concurrent.Sleep;
import com.trendrr.oss.exceptions.TrendrrParseException;
import com.trendrr.strest.StrestException;


/**
 * @author Dustin Norlander
 * @created Feb 14, 2012
 * 
 */
public class CacheFilter extends CheshireFilter {

	protected Log log = LogFactory.getLog(CacheFilter.class);

	protected static boolean disabled = false;
	protected int errorTimeoutSeconds = 30;


	/**
	 * returns the persistence provider
	 * @param controller
	 * @return
	 */
	protected TrendrrCache getCachePersistence(CheshireController controller) {
		return TrendrrCaches.getCacheOrDefault("cache", controller);
	}
	
	private boolean shouldCache(CheshireController controller) {
		if (disabled) {
			return false;
		}
		
		if (controller.cacheTimeoutSeconds() < 1) {
//			log.info("no timeout");
			return false;
		}
		
		if (controller.getRequest().getMethod() != HttpMethod.GET) {
			//don't cache non GET methods
			return false;
		}
		
		if (controller.isSkipExecution()) {
			return false;
		}
		
		String key = controller.getCacheKey();
		if (key == null || key.length() ==0) {
//			log.info("null key");
			return false;
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see com.trendrr.cheshire.filters.CheshireFilter#before(com.trendrr.cheshire.CheshireController)
	 */
	@Override
	public void before(CheshireController controller) throws StrestException {
		if (!this.shouldCache(controller)) {
			return;
		}
		
		String key = controller.getCacheKey();
		if (key == null || key.length() == 0) {
			log.info("Cache key is empty, not caching");
			return;
		}
		
		TrendrrCache cache = getCachePersistence(controller);
		if (cache == null) {
			log.warn("No cache provider");
			return;
		}
		
		TrendrrCacheItem content = this.cacheLoad(cache, key);
//		log.warn("Attempted cache load in : " + (new Date().getTime()-start.getTime()));
		if (content == null) {
			controller.getTxnStorage().put("cache_key", key);
			//need to execute the controller and then save.
			controller.getAccessLog().put("cached", false);
		} else {
			controller.setResponseBytes(content.getMetadata().getString("mime"), content.getContentBytes());
			controller.setResponseStatus(content.getMetadata().getInteger("sts"), content.getMetadata().getString("sts_msg"));
			controller.setSkipExecution(true);
			controller.getAccessLog().put("cached", true);
		}
	}

	/**
	 * will save the response
	 * @param controller
	 * @param timeout
	 * @param res
	 */
	protected void save(CheshireController controller, int timeout, HttpResponse res) {
		if (controller == null)
			return;
		
		TrendrrCache cache = getCachePersistence(controller);
		if (cache == null) {
			log.warn("No cache provider");
			return;
		}
		
		String key = (String)controller.getTxnStorage().get("cache_key");
		if (key == null) {
			log.warn("cache key was null in the txn storage. wtf?");
			key = controller.getCacheKey();//wtf?
		}
		//else we refresh the cache
		
		DynMap meta = new DynMap();
		meta.put("at", new Date());
		meta.put("mime", res.getHeader("Content-Type"));
		meta.put("sts", res.getStatus().getCode());
		meta.put("sts_msg", res.getStatus().getReasonPhrase());
		
		TrendrrCacheItem c = TrendrrCacheItem.instance(meta, res.getContent().array());
		Date contentexpire = Timeframe.SECONDS.add(new Date(), timeout);
		cache.set(key, c.serialize(), contentexpire);
	}
	

	
	
	/**
	 * Will load the cached data. 
	 * will return null if the current thread should feed the cache
	 * otherwise will wait for another thread to 
	 * initialize it.
	 * 
	 * @param meta
	 * @return
	 * @throws StrestException 
	 */
	protected TrendrrCacheItem cacheLoad(TrendrrCache cache, String key) throws StrestException {
		try {
	//		log.warn("Loading cache: " + key);
			Date maxWait = Timeframe.SECONDS.add(new Date(), 120); //we fail if we get nothing in 2 minutes.
			while (new Date().before(maxWait)) {
				
				TrendrrCacheItem content = null;
				byte[] bytes = (byte[])cache.get("cheshire.cache", key);
				if (bytes == null) {
					content = new TrendrrCacheItem();
					content.getMetadata().put("INIT", true);
					if (cache.setIfAbsent(key, content.serialize(), Timeframe.SECONDS.add(new Date(), 15))) {
						return null; //our job to initialize the cache.
					}
				} else {
					content = TrendrrCacheItem.deserialize(bytes);
				}
				
				if (content != null && !content.getMetadata().getBoolean("INIT", false)) {
					//CACHE HIT!
					return content;
				}
				
				//ok, so I guess we have to wait.
				do {
					log.warn("waiting on someone else to reset the cache. ");
					Sleep.millis(300);
					
					bytes = (byte[])cache.get("cheshire.cache", key);
					if (bytes != null) {
						content = TrendrrCacheItem.deserialize(bytes);
					} else {
						content = null;
					}
				} while(content != null && content.getMetadata().getBoolean("INIT", false) && new Date().before(maxWait));
				if (content != null) {
					//CACHE HIT!
					return content;
				}
			}
		} catch (TrendrrParseException e) {
			log.warn("POISON CACHE! : " + key, e);
			return null; //we will reset the cache.
		}
		throw new StrestException("CACHE ERROR! Big problem with the cache, unable to get data in 2 minutes for key: " + key);
	}
	
	/* (non-Javadoc)
	 * @see com.trendrr.cheshire.filters.CheshireFilter#after(com.trendrr.cheshire.CheshireController)
	 */
	@Override
	public void after(CheshireController controller) throws StrestException {
		if (!this.shouldCache(controller)) {
			return;
		}
		this.save(controller, controller.cacheTimeoutSeconds(), controller.getResponse());
	}

	/* (non-Javadoc)
	 * @see com.trendrr.cheshire.filters.CheshireFilter#error(com.trendrr.cheshire.CheshireController, org.jboss.netty.handler.codec.http.HttpResponse, java.lang.Exception)
	 */
	@Override
	public void error(CheshireController controller, HttpResponse response,
			Exception exception) {
		//if the controller is null, its a 404 or something we don't care about.
		if (controller == null || !this.shouldCache(controller))
			return;
		this.save(controller, this.errorTimeoutSeconds, response);
	}
}