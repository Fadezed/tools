package com.fingard.tools.stacktop;

import com.fingard.tools.stacktop.pojo.InteractiveTask;
import com.fingard.tools.stacktop.pojo.VmDetailView;
import com.fingard.tools.stacktop.pojo.VMInfo;
import com.fingard.tools.stacktop.util.Formats;
import com.fingard.tools.stacktop.util.OptionAdvanceParser;
import com.fingard.tools.stacktop.util.Utils;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
//import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;

/**
 * @author zed
 */
//@SpringBootApplication
public class StackTopApplication {
	/**
	 * 版本号
	 */
	private static final String VERSION = "1.0.0";
	private static final String HELP_PARAMETER = "help";
	private static final String JMX_URL = "jmxurl";
	/**
	 *
	 */
	public VmDetailView view;
	private Thread mainThread;

	private Integer interval;
	private int maxIterations = -1;

	private volatile boolean needMoreInput = false;
	private long sleepStartTime;
	public static void main(String[] args) {
//		SpringApplication.run(StackTopApplication.class, args);
		try {
			// 1. create option parser
			OptionParser parser = OptionAdvanceParser.createOptionParser();
			OptionSet optionSet = parser.parse(args);

			if (optionSet.has(HELP_PARAMETER)) {
				printHelper(parser);
				System.exit(0);
			}

			// 2. create vm info
			String pid = OptionAdvanceParser.parsePid(parser, optionSet);

			String jmxHostAndPort = null;
			if (optionSet.hasArgument(JMX_URL)) {
				jmxHostAndPort = (String) optionSet.valueOf(JMX_URL);
			}

			VMInfo vminfo = VMInfo.processNewVM(pid, jmxHostAndPort);
			if (vminfo.state != VMInfo.VMInfoState.ATTACHED) {
				System.out.println("\n" + Formats.red("ERROR: Could not attach to process, see the solution in README"));
				return;
			}

			// 3. create view
			VmDetailView.ThreadInfoMode threadInfoMode = OptionAdvanceParser.parseThreadInfoMode(optionSet);
			VmDetailView.OutputFormat format = OptionAdvanceParser.parseOutputFormat(optionSet);
			VmDetailView.ContentMode contentMode = OptionAdvanceParser.parseContentMode(optionSet);

			Integer width = null;
			if (optionSet.hasArgument("width")) {
				width = (Integer) optionSet.valueOf("width");
			}

			Integer interval = OptionAdvanceParser.parseInterval(optionSet);

			VmDetailView view = new VmDetailView(vminfo, format, contentMode, threadInfoMode, width, interval);

			if (optionSet.hasArgument("limit")) {
				view.threadLimit = (Integer) optionSet.valueOf("limit");
			}

			if (optionSet.hasArgument("filter")) {
				view.threadNameFilter = (String) optionSet.valueOf("filter");
			}

			// 4. create main application
			StackTopApplication app = new StackTopApplication();
			app.mainThread = Thread.currentThread();
			app.view = view;
			app.updateInterval(interval);

			if (optionSet.hasArgument("n")) {
				app.maxIterations = (Integer) optionSet.valueOf("n");
			}

			// 5. console/cleanConsole mode start thread to get user input
			if (format != VmDetailView.OutputFormat.text) {
				InteractiveTask task = new InteractiveTask(app);
				// 前台运行，接受用户输入时才启动交互进程
				if (task.inputEnabled()) {
					view.displayCommandHints = true;
					if (app.maxIterations == -1) {
						Thread interactiveThread = new Thread(task, "InteractiveThread");
						interactiveThread.setDaemon(true);
						interactiveThread.start();
					}
				} else {
					// 后台运行，输出重定向到文件时，转为没有ansi码的干净模式
					format = VmDetailView.OutputFormat.cleanConsole;
				}
			}

			// 6. cleanConsole/text mode, 屏蔽ansi码
			if (!format.ansi) {
				Formats.disableAnsi();
				if (format == VmDetailView.OutputFormat.cleanConsole) {
					Formats.setCleanClearTerminal();
				} else {
					Formats.setTextClearTerminal();
				}
			}

			// 7. run app
			app.run(view);
		} catch (Exception e) {
			e.printStackTrace(System.out);
			System.out.flush();
		}
	}

	private void run(VmDetailView view) throws Exception {
		try {
			// System.out 设为Buffered，需要使用System.out.flush刷新
			System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out)), false));

			int iterations = 0;
			while (!view.shouldExit()) {
				waitForInput();
				view.printView();
				if (view.shouldExit()) {
					break;
				}

				System.out.flush();

				if (maxIterations > 0 && iterations >= maxIterations) {
					break;
				}

				// 第一次最多只等待3秒
				int sleepSeconds = (iterations == 0) ? Math.min(3, interval) : interval;

				iterations++;
				sleepStartTime = System.currentTimeMillis();
				Utils.sleep(sleepSeconds * 1000L);
			}
			System.out.println("");
			System.out.flush();
		} catch (NoClassDefFoundError e) {
			e.printStackTrace(System.out);
			System.out.println(Formats.red("ERROR: Some JDK classes cannot be found."));
			System.out.println("       Please check if the JAVA_HOME environment variable has been set to a JDK path.");
			System.out.println("");
			System.out.flush();
		}
	}

	public static void printHelper(OptionParser parser) {
		try {
			System.out.println("StackTop " + VERSION + " - java monitoring for the command-line");
			System.out.println("Usage: stackTop.sh [options...] <PID>");
			System.out.println("");
			parser.printHelpOn(System.out);
		} catch (IOException ignored) {

		}
	}

	public void exit() {
		view.shoulExit();
		mainThread.interrupt();
	}

	public void interruptSleep() {
		mainThread.interrupt();
	}

	public void preventFlush() {
		needMoreInput = true;
	}

	public void continueFlush() {
		needMoreInput = false;
	}

	private void waitForInput() {
		while (needMoreInput) {
			Utils.sleep(1000);
		}
	}

	public int nextFlushTime() {
		return Math.max(0, interval - (int) ((System.currentTimeMillis() - sleepStartTime) / 1000));
	}

	public void updateInterval(int interval) {
		this.interval = interval;
		view.interval = interval;
	}

	public int getInterval() {
		return interval;
	}

}
