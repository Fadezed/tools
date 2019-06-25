package com.fingard.tools.top.data.jmx;

import com.sun.management.GarbageCollectorMXBean;

import javax.management.MBeanServerConnection;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * @author zed
 * JmxGarbageCollectorManager
 */
public class JmxGarbageCollectorManager {

	private GarbageCollectorMXBean ygcMXBean = null;
	private GarbageCollectorMXBean fgcMXBean = null;

	public static String getByGcName(String gcName, String defaultName) {

		return defaultName;
	}

	public JmxGarbageCollectorManager(MBeanServerConnection connection) throws IOException {
		List<GarbageCollectorMXBean> gcMXBeans = ManagementFactory.getPlatformMXBeans(connection,
				GarbageCollectorMXBean.class);
		for (GarbageCollectorMXBean gcMXBean : gcMXBeans) {
			String gcName = gcMXBean.getName();
			if ("Copy".equals(gcName) || "PS Scavenge".equals(gcName) || "ParNew".equals(gcName)
					|| "G1 Young Generation".equals(gcName)) {
				ygcMXBean = gcMXBean;
			} else if ("MarkSweepCompact".equals(gcName) || "PS MarkSweep".equals(gcName)
					|| "ConcurrentMarkSweep".equals(gcName) || "G1 Old Generation".equals(gcName)) {
				fgcMXBean = gcMXBean;
			} else {
				ygcMXBean = gcMXBean;
			}
		}
	}

	public synchronized GarbageCollectorMXBean getYoungCollector() {
		return ygcMXBean;
	}

	public synchronized GarbageCollectorMXBean getFullCollector() {
		return fgcMXBean;
	}
}
