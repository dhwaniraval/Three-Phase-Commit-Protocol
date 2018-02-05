package com.aos.listener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.stream.events.StartDocument;

import com.aos.model.StringConstants;
import com.aos.utility.FileAccessor;
import com.aos.utility.SharedDataAmongCoordThreads;

/**
 *  
 */

public class CoordinatorServerHandler extends Thread {
	/**
	 * Variables required for establishing connections
	 */
	private Socket cohortSocket = null;
	private PrintWriter printWriter = null;
	private DataInputStream dataInputStream = null;

	/**
	 * Variables required for computation
	 */
	private String stringInputStream;
	private int maxCohort;
	private int processId;

	/**
	 * Boolean variables required to not allow the coordinator to send the same
	 * data multiple times to all cohorts
	 */
	private boolean isAborted;
	private boolean isCommitted;
	private boolean isCommitRequest;
	private boolean isPrepareSentToAllCohorts;
	private boolean coordinatorFail = false;

	/**
	 * Variable to access shared data among different handler threads
	 */
	private SharedDataAmongCoordThreads sharedDataAmongCoordThreads;

	/**
	 * Variables to access the log files for states of three phase protocol and
	 * output files
	 */
	private File stateLogFile;
	private File outputLogFile;

	/**
	 * Variables to calculate timeout
	 */
	private DateFormat dateFormat1;
	private Date date1;
	private DateFormat dateFormat2;
	private Date date2;
	private boolean isTimeOUTinQ = false;
	private long duration = 0;

	/**
	 * Variable to access file methods
	 */
	private FileAccessor fileaccessor;

	/**
	 * A parameterized constructor that initializes its local variables
	 */
	public CoordinatorServerHandler(Socket cohortSocket, int maxCohort, DataInputStream inputStream, int pId,
			SharedDataAmongCoordThreads sharedDataAmongCoordThreads) {
		this.cohortSocket = cohortSocket;
		this.maxCohort = maxCohort;
		this.dataInputStream = inputStream;
		this.processId = pId;
		this.sharedDataAmongCoordThreads = sharedDataAmongCoordThreads;

		isAborted = false;
		isCommitted = false;
		isCommitRequest = false;
		isPrepareSentToAllCohorts = false;

		stateLogFile = new File(System.getProperty("user.dir") + "/StateInfo_Coordinator");
		outputLogFile = new File(System.getProperty("user.dir") + "/Output_Coordinator");

		fileaccessor = new FileAccessor();
	}

	/**
	 * Main run method
	 */
	@Override
	public void run() {
		while (true) {
			try {
				printWriter = new PrintWriter(cohortSocket.getOutputStream());
				printWriter.println(processId + StringConstants.SPACE);
				printWriter.flush();

				if (sharedDataAmongCoordThreads.isCommitRequest() && !isCommitRequest && !coordinatorFail) {

					// Failure for Q1 to a1
					if (sharedDataAmongCoordThreads.getTransactionID() % 5 == 0) {
						System.out.println("On every fifth transaction, Coordinator FAILS.");

						Thread.sleep(30000);

						printWriter.println("");
						printWriter.flush();
						break;

					} else if (sharedDataAmongCoordThreads.isQ1TimeOut() && !isAborted) {

						// Timeout for Q1 to a1

						isAborted = true;
						System.out.println("Coordinator Timeout. Transition from Q1 --> a1");

						abortCoordinatorOnTimeOut(stateLogFile, sharedDataAmongCoordThreads, printWriter);

						System.out.println();
						System.out.println("...Coordinator Thread terminates...");
						System.out.println();

						break;

					} else {
						int countTimeOut = 0;
						isCommitRequest = true;
						printWriter.println(generateMessageString(StringConstants.MESSAGE_COMMIT_REQUEST,
								sharedDataAmongCoordThreads));
						printWriter.flush();

						fileaccessor.writeToStateLogFile(stateLogFile, StringConstants.STATE_W1 + StringConstants.SPACE
								+ sharedDataAmongCoordThreads.getCommitedValue());

						System.out.println(
								"Coordinator sent COMMIT_REQUEST message to all Cohorts. The state chagnges from Q1 --> W1");
						System.out.println("TIMEOUT for Coordinator to receive AGREED and ACK is 20 seconds");
						date1 = new Date();

						if (!sharedDataAmongCoordThreads.isUpdateTime()) {
							sharedDataAmongCoordThreads.setUpdateTime(true);
							sharedDataAmongCoordThreads.setStartTime(date1.getTime());
						}

						while ((stringInputStream = dataInputStream.readLine()) != null) {
							if (stringInputStream.split(StringConstants.SPACE)[0]
									.startsWith(StringConstants.MESSAGE_AGREED)) {

								if (sharedDataAmongCoordThreads.getTransactionID() % 7 == 0) {
									System.out.println("For every seventh transaction, Coordinator FAILS. ");
									Thread.sleep(20000);

								} else {
									sharedDataAmongCoordThreads.incrementAgree();
									System.out.println("Coordinator received AGREED from "
											+ sharedDataAmongCoordThreads.getCountAgreeFromCohort() + " Cohort");

									if (sharedDataAmongCoordThreads.getCountAgreeFromCohort() != maxCohort
											&& !isCommitted && !sharedDataAmongCoordThreads.isW1TimeOut()) {
										sharedDataAmongCoordThreads.incrementTimeOut();

										System.out.println(
												"Coordinator waits for AGREED from all Cohorts for 20 seconds");

										Thread.sleep(10000);

										if (sharedDataAmongCoordThreads.getCountTimeOut() == 2) {
											sharedDataAmongCoordThreads.setW1TimeOut(true);
											isAborted = true;

											System.out.println("Coordinator TimeOut. Transition from w1 --> a1");

											abortCoordinatorOnTimeOut(stateLogFile, sharedDataAmongCoordThreads,
													printWriter);

											System.out.println();
											System.out.println("...Coordinator Thread terminates...");
											System.out.println();

											break;
										}
									}
								}
							}

							// Received AGREED Message from all cohorts
							if (sharedDataAmongCoordThreads.getCountAgreeFromCohort() == maxCohort
									&& !isPrepareSentToAllCohorts && !coordinatorFail) {
								isPrepareSentToAllCohorts = true;
								System.out.println(
										"Coordinator received AGREED from all Cohorts. Transition from w1 --> p1");

								fileaccessor.writeToStateLogFile(stateLogFile, StringConstants.STATE_P1
										+ StringConstants.SPACE + sharedDataAmongCoordThreads.getCommitedValue());

								printWriter.println(generateMessageString(StringConstants.MESSAGE_PREPARE,
										sharedDataAmongCoordThreads));
								printWriter.flush();

								System.out.println("Coordinator sent PREPARE to all Cohorts");
								date1 = new Date();

								if (!sharedDataAmongCoordThreads.isUpdateTime()) {
									sharedDataAmongCoordThreads.setUpdateTime(true);
									sharedDataAmongCoordThreads.setStartTime(date1.getTime());
								}
							}

							// Received ACK Message
							if (stringInputStream.split(StringConstants.SPACE)[0]
									.startsWith(StringConstants.MESSAGE_ACK) && !coordinatorFail) {
								sharedDataAmongCoordThreads.incrementAck();
								System.out.println("Coordinator received ACK from "
										+ sharedDataAmongCoordThreads.getCountAckFromCohort() + " Cohort(s)");

								while (sharedDataAmongCoordThreads.getCountAckFromCohort() != maxCohort) {
									Thread.sleep(1000);
								}
							}

							// Received ACK Message from all
							if (sharedDataAmongCoordThreads.getCountAckFromCohort() == maxCohort && !coordinatorFail) {
								date2 = new Date();

								if (!sharedDataAmongCoordThreads.isUpdateEndTime()) {
									sharedDataAmongCoordThreads.setUpdateEndTime(true);
									sharedDataAmongCoordThreads.setEndTime(date2.getTime());
								}

								duration = sharedDataAmongCoordThreads.getEndTime()
										- sharedDataAmongCoordThreads.getStartTime();

								// Timeout
								if (duration > 20000 && sharedDataAmongCoordThreads.getCountAckFromCohort() == maxCohort
										&& !coordinatorFail && !isAborted) {

									isAborted = true;
									System.out.println("Coordinator TIMEOUT. Transition from p1 --> a1");

									abortCoordinatorOnTimeOut(stateLogFile, sharedDataAmongCoordThreads, printWriter);
									System.out.println("Transition between the states for Coordinator is : p1 --> a1");

									System.out.println("...Coordinator Thread terminates...");
									System.out.println();
									break;

								} else if (duration <= 20000
										&& sharedDataAmongCoordThreads.getCountAckFromCohort() == maxCohort
										&& !isCommitted && !coordinatorFail) {
									isCommitted = true;

									if (sharedDataAmongCoordThreads.getTransactionID() % 9 == 0) {
										System.out.println("For every ninth transaction, the Coordinator FAILS");
										Thread.sleep(20000);

									} else {
										printWriter.println(generateMessageString(StringConstants.MESSAGE_COMMIT,
												sharedDataAmongCoordThreads));
										printWriter.flush();

										System.out.println("Coordinator sent COMMIT to all cohorts");

										if (!sharedDataAmongCoordThreads.isWriteOutputFile()) {
											sharedDataAmongCoordThreads.setWriteOutputFile(true);

											fileaccessor.writeToStateLogFile(stateLogFile, "");
											fileaccessor.writeToOutputFile(outputLogFile,
													sharedDataAmongCoordThreads.getTransactionID()
															+ StringConstants.SPACE
															+ sharedDataAmongCoordThreads.getCommitedValue()
															+ StringConstants.SPACE + "Committed");
										}

										System.out.println(
												"Transition between the states for Coordinator is : p1 --> c1");

										System.out.println("...Coordinator Thread terminates...");
										System.out.println();
										break;
									}

								}
							}

							// Received ABORT Message
							if (stringInputStream.split(StringConstants.SPACE)[0]
									.startsWith(StringConstants.MESSAGE_ABORT) && !isAborted && !coordinatorFail) {
								isAborted = true;

								if (!sharedDataAmongCoordThreads.isAborted()
										&& !sharedDataAmongCoordThreads.isWriteFile()) {
									sharedDataAmongCoordThreads.setAborted(true);
									sharedDataAmongCoordThreads.setWriteFile(true);

									fileaccessor.writeToStateLogFile(stateLogFile, "");
									fileaccessor.writeToOutputFile(outputLogFile,
											sharedDataAmongCoordThreads.getTransactionID() + StringConstants.SPACE
													+ sharedDataAmongCoordThreads.getCommitedValue()
													+ StringConstants.SPACE + "Aborted");
								}
							}

							if (sharedDataAmongCoordThreads.isAborted() && !coordinatorFail) {
								printWriter.println(generateMessageString("ABORT", sharedDataAmongCoordThreads));
								printWriter.flush();

								System.out.println("Coordinator received ABORT from one or more cohorts.");
								System.out.println("Coordinator sent ABORT to all cohorts.");

								System.out.println("Transition between the states for Coordinator is : w1 --> a1");

								System.out.println("...Coordinator Thread terminates...");
								System.out.println();
								break;
							}

						}

					}
					// }
				}

				// After recovery, abort
				if ((sharedDataAmongCoordThreads.isAbortAfterRecovery() && !isAborted && !coordinatorFail)) {

					isAborted = true;
					isTimeOUTinQ = true;

					if (!sharedDataAmongCoordThreads.isAborted() && !sharedDataAmongCoordThreads.isWriteFile()) {
						sharedDataAmongCoordThreads.setAborted(true);
						sharedDataAmongCoordThreads.setWriteFile(true);

						fileaccessor.writeToStateLogFile(stateLogFile, "");
						fileaccessor.writeToOutputFile(outputLogFile,
								sharedDataAmongCoordThreads.getTransactionID() + StringConstants.SPACE
										+ sharedDataAmongCoordThreads.getCommitedValue() + StringConstants.SPACE
										+ "Aborted");
					}

					System.out.println(
							"After Recovery, transition between the states for Coordinator is either : Q1 --> a1 or W1 --> a1");

					System.out.println("...Coordinator Thread terminates...");
					System.out.println();

					break;
				}

				// After recovery, commit
				if (sharedDataAmongCoordThreads.isCommitAfterRecovery() && !isCommitted && !coordinatorFail) {
					isCommitted = true;

					if (!sharedDataAmongCoordThreads.isWriteOutputFile()) {
						sharedDataAmongCoordThreads.setWriteOutputFile(true);

						fileaccessor.writeToStateLogFile(stateLogFile, "");
						fileaccessor.writeToOutputFile(outputLogFile,
								sharedDataAmongCoordThreads.getTransactionID() + StringConstants.SPACE
										+ sharedDataAmongCoordThreads.getCommitedValue() + StringConstants.SPACE
										+ "Committed");
					}

					System.out.println("After recovery, transition between the states for Coordinator is : p1 --> c1");
					System.out.println("...Coordinator Thread terminates...");
					System.out.println();
					break;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Method to generate messages sent by Coordinator
	 */
	public String generateMessageString(String messageType, SharedDataAmongCoordThreads data) {
		return messageType + StringConstants.SPACE + data.getTransactionID() + StringConstants.SPACE
				+ +data.getCommitedValue();
	}

	/**
	 * Method to write into state log files
	 */
	public void writeToStateLogFile(File stateFile, String state) {
		FileWriter stateFileWriter = null;
		BufferedWriter bufferedWriter = null;
		try {
			stateFileWriter = new FileWriter(stateFile);
			bufferedWriter = new BufferedWriter(stateFileWriter);

			stateFileWriter.append(state);
			bufferedWriter.flush();
			bufferedWriter.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Method to write into output log files
	 */
	public void writeToOutputFile(File outputFile, String newOutput) {
		FileWriter outputFileWriter = null;
		BufferedWriter outputBufferedFileWriter = null;
		FileReader outputReader;
		BufferedReader outputBufferedReader = null;

		String s;
		try {
			outputReader = new FileReader(outputFile);
			outputBufferedReader = new BufferedReader(outputReader);
			List<String> previousEntries = new ArrayList<>();

			while ((s = outputBufferedReader.readLine()) != null) {
				StringBuilder stringB = new StringBuilder();

				for (String string : s.split(StringConstants.SPACE)) {
					stringB.append(string + StringConstants.SPACE);
				}
				previousEntries.add(stringB.toString());
			}
			previousEntries.add(newOutput);

			outputFileWriter = new FileWriter(outputFile);
			outputBufferedFileWriter = new BufferedWriter(outputFileWriter);

			for (int i = 0; i < previousEntries.size(); i++) {
				outputBufferedFileWriter.append(previousEntries.get(i));
				outputBufferedFileWriter.append("\n");
			}

			outputBufferedFileWriter.flush();
			outputBufferedFileWriter.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Method to abort the coordinator on timeout
	 */
	public void abortCoordinatorOnTimeOut(File stateLogFile, SharedDataAmongCoordThreads sharedDataAmongCoordThreads,
			PrintWriter printWriter) {

		if (!sharedDataAmongCoordThreads.isTimeOut()) {
			sharedDataAmongCoordThreads.setTimeOut(true);

			writeToStateLogFile(stateLogFile, "");
			writeToOutputFile(outputLogFile, sharedDataAmongCoordThreads.getTransactionID() + StringConstants.SPACE
					+ sharedDataAmongCoordThreads.getCommitedValue() + StringConstants.SPACE + "ABORTED");
		}

		printWriter.println(generateMessageString(StringConstants.MESSAGE_ABORT, sharedDataAmongCoordThreads));
		printWriter.flush();

		System.out.println("Coordinator sent ABORT to all Cohorts");
	}

}
