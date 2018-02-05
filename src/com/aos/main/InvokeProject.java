package com.aos.main;

import com.aos.listener.Coordinator;
import com.aos.listener.Cohort;
import com.aos.utility.ReadConfigFile;

/**
 * 
 * @author dsr170230
 * @version 7.0
 */

public class InvokeProject {
	private static ReadConfigFile extractFromConfigFile;
	private static Coordinator coordinatorProcess;
	private static Cohort otherProcess;

	/*
	 * private static CohortSharedData sharedData = new CohortSharedData();
	 */

	/**
	 * 
	 */
	public static void main(String[] args) {
		if (args[0].equals("-c")) {
			extractFromConfigFile = new ReadConfigFile(true);
			coordinatorProcess = new Coordinator();
			coordinatorProcess = extractFromConfigFile.getConfigFileData(args[1]);
			coordinatorProcess.start();
		} else {
			extractFromConfigFile = new ReadConfigFile(false);
			otherProcess = new Cohort();
			otherProcess = extractFromConfigFile.getConfigDataForProcess(args[0]);
			otherProcess.start();
		}
	}
}
