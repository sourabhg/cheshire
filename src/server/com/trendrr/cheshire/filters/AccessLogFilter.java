/**
 * 
 */
package com.trendrr.cheshire.filters;

import java.net.SocketAddress;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jboss.netty.handler.codec.http.HttpResponse;

import com.trendrr.oss.DynMap;
import com.trendrr.oss.Timeframe;
import com.trendrr.oss.TypeCast;
import com.trendrr.oss.appender.RollingFileAppender;
import com.trendrr.oss.concurrent.LazyInit;
import com.trendrr.strest.StrestException;
import com.trendrr.strest.server.StrestController;
import com.trendrr.strest.server.StrestControllerFilter;
import com.trendrr.strest.server.v2.models.StrestResponse;


/**
 * @author Dustin Norlander
 * @created Aug 29, 2011
 * 
 */
public class AccessLogFilter implements StrestControllerFilter {

	protected static Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

	protected RollingFileAppender appender = null;
	protected LazyInit appenderInit = new LazyInit();
	
	/* (non-Javadoc)
	 * @see com.trendrr.strest.server.StrestControllerFilter#before(com.trendrr.strest.server.StrestController)
	 */
	@Override
	public void before(StrestController controller) throws StrestException {
		if (appenderInit.start()) {
			try {
				this.init(controller.getServerConfig().getMap("access_log", new DynMap()));
			} finally {
				appenderInit.end();
			}
		}
		
		
		controller.getTxnStorage().put("accesslog_start", new Date());

	}

	protected void init(DynMap accessLogConfig) {
		String[] time = accessLogConfig.getString("file_time_scale", "6 hours").split(" ");
		appender = new RollingFileAppender(Timeframe.instance(time[1]), TypeCast.cast(Integer.class, time[0]), 
				accessLogConfig.getInteger("max_files", 20),
				accessLogConfig.getString("filename", "/var/logs/strest/access.log"));
	}
	
	/**
	 * Subclasses can override this to add any additional logging fields (or take them away).
	 * if this returns null, the message will not be logged.
	 * @param controller
	 * @return
	 */
	protected DynMap toLog(StrestController controller) {
		Date start = (Date)controller.getTxnStorage().remove("accesslog_start");
		DynMap mp = new DynMap();
		mp.put("timestamp", start);
		mp.put("millis", (new Date().getTime() - start.getTime()));
		mp.put("uri", controller.getRequest().getUri());
		mp.put("strest", controller.isStrest());
		mp.put("method", controller.getRequest().getMethod().toString());
		if (!controller.getChannelConnection().isConnected()) {
			mp.put("error", "channel disconnected");
		} else {
			mp.put("host", controller.getChannelConnection().getRemoteAddress());
		}
		return mp;
	}
	/* (non-Javadoc)
	 * @see com.trendrr.strest.server.StrestControllerFilter#after(com.trendrr.strest.server.StrestController)
	 */
	@Override
	public void after(StrestController controller) throws StrestException {
		try {
			DynMap row = this.toLog(controller);
			if (row == null)
				return;
			appender.append(row.toJSONString() + "\n");
		} catch (Exception e) {
			log.error("Caught", e);
		}
	}

	/* (non-Javadoc)
	 * @see com.trendrr.strest.server.StrestControllerFilter#error(com.trendrr.strest.server.StrestController, org.jboss.netty.handler.codec.http.HttpResponse, java.lang.Exception)
	 */
	@Override
	public void error(StrestController controller, StrestResponse response,
			Exception exception) {
		// TODO Auto-generated method stub

	}
}
