package de.vommond.lunarmare.util;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;

import java.lang.management.ManagementFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.management.OperatingSystemMXBean;

@SuppressWarnings("restriction")
public class MonitoringVerticle  extends AbstractVerticle{
	
	public static final String CREATED = "created";

	public static final String TOTAL_MEMORY = "totalMemory";

	public static final String MAX_MEMORY = "maxMemory";

	public static final String FREE_MEMORY = "freeMemory";

	public static final String SYSTEM_LOAD_AVERAGE = "SystemLoadAverage";

	public static final String SYSTEM_CPU_LOAD = "SystemCpuLoad";

	public static final String PROCESS_CPU_TIME = "ProcessCpuTime";
	
	public static final String TOTAL_PHYSICAL_MEMORY_SIZE = "TotalPhysicalMemorySize";

	public static final String FREE_PHYSICAL_MEMORY_SIZE = "FreePhysicalMemorySize";

	public static final String PROCESS_CPU_LOAD = "ProcessCpuLoad";

	private Logger logger = LoggerFactory.getLogger(MonitoringVerticle.class);

	private OperatingSystemMXBean osBean;
	
	private long timerID;
	
	public final static String TOPIC = "Lunarmare.OsPerformance";
	
	@Override
	public void start() {
		this.logger.info("start() > enter");
		
		try{
			
			osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
			
			timerID = vertx.setPeriodic(60*1000, l ->{
				this.measure();
			});
			
		} catch(Exception e){
			logger.error("Could not start");
		}
		
		this.logger.debug("start() > exit");
	}
	
	
	@Override
	public void stop() {
		this.logger.info("stop() > enter");
		
		vertx.cancelTimer(timerID);
		
		this.logger.debug("stop() > exit");
	}
	
	private void measure(){
		this.logger.debug("measure() > enter");
		
		try{
			JsonObject messsage = new JsonObject()
				.put(PROCESS_CPU_LOAD, osBean.getProcessCpuLoad())
				.put(FREE_PHYSICAL_MEMORY_SIZE, osBean.getFreePhysicalMemorySize())
				.put(PROCESS_CPU_TIME, osBean.getProcessCpuTime())
				.put(SYSTEM_CPU_LOAD, osBean.getSystemCpuLoad())
				.put(SYSTEM_LOAD_AVERAGE, osBean.getSystemLoadAverage())
				.put(TOTAL_PHYSICAL_MEMORY_SIZE, osBean.getTotalPhysicalMemorySize())
				.put(FREE_MEMORY, Runtime.getRuntime().freeMemory())
				.put(MAX_MEMORY, Runtime.getRuntime().maxMemory())
				.put(TOTAL_MEMORY, Runtime.getRuntime().totalMemory())
				
				.put(CREATED, System.currentTimeMillis());
	
		
			vertx.eventBus().publish(TOPIC, messsage);
		} catch(Exception e){
			logger.error("measure() Error : "+ e.getLocalizedMessage()); 
		}
		
		
		
	}

}
