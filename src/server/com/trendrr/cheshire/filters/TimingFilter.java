/**
 * 
 */
package com.trendrr.cheshire.filters;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jboss.netty.handler.codec.http.HttpResponse;

import com.trendrr.strest.StrestException;
import com.trendrr.strest.server.StrestController;
import com.trendrr.strest.server.StrestControllerFilter;
import com.trendrr.strest.server.v2.models.StrestResponse;


/**
 * @author Dustin Norlander
 * @created May 17, 2011
 * 
 */
public class TimingFilter implements StrestControllerFilter {

	protected static Logger log = LoggerFactory.getLogger(TimingFilter.class);

	/* (non-Javadoc)
	 * @see com.trendrr.strest.server.StrestControllerFilter#before(com.trendrr.strest.server.StrestController)
	 */
	@Override
	public void before(StrestController controller) throws StrestException {
		controller.getTxnStorage().put("timing_filter_start", new Date());
	}

	/* (non-Javadoc)
	 * @see com.trendrr.strest.server.StrestControllerFilter#after(com.trendrr.strest.server.StrestController)
	 */
	@Override
	public void after(StrestController controller) throws StrestException {
		Date start = (Date)controller.getTxnStorage().remove("timing_filter_start");
		if (start == null)
			return;
		this.timingResult(controller, new Date().getTime()-start.getTime());
	}

	/**
	 * Override this to do something else with the timing information.
	 * 
	 * Examples: logging, tracking long running actions, ect.
	 * 
	 * @param controller
	 * @param millis
	 */
	public void timingResult(StrestController controller, long millis) {
		StringBuilder str = new StringBuilder();
		str.append("*************** Action COMPLETE ****\n");
		str.append("Route : " );
		for (String r : controller.routes()) {
			str.append(r + ",");
		}
		str.append("\nParams: ");
		str.append( controller.getParams().toJSONString());
		str.append("\nMillis: ");
		str.append(millis);
		str.append("\n************************************\n");
		log.info(str.toString());
	}
	
	/* (non-Javadoc)
	 * @see com.trendrr.strest.server.StrestControllerFilter#error(com.trendrr.strest.server.StrestController, org.jboss.netty.handler.codec.http.HttpResponse, java.lang.Exception)
	 */
	@Override
	public void error(StrestController controller, StrestResponse response,
			Exception exception) {
		if (controller == null)
			return;
		Date start = (Date)controller.getTxnStorage().remove("timing_filter_start");
		if (start == null)
			return;
		this.timingResult(controller, new Date().getTime()-start.getTime());
	}
}
