package com.aos.listener;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Scanner;

import com.aos.model.StringConstants;
import com.aos.utility.FileAccessor;

/**
 * */
public class Cohort {
	/**
	 * Variables to establish a connection with coordinator and neighboring
	 * process
	 */
	private int PORT = 5005;
	private int coordinatorPort = 9001;

	private Socket cohortSocket = null;
	private DataInputStream cohortDataInputStream = null;
	private PrintStream cohortPrintStream = null;

	/**
	 * Variables to store information from the Config file
	 */
	private int maxCohort;
	private String coordinatorHostName;

	/**
	 * Variables required for computation
	 */
	private int pId;
	private String readInputStream;

	private boolean isAborted;
	private String choice;
	private boolean sentAck;
	private boolean startComputation;
	private boolean recoveryDone;
	private boolean recoveryCheckDone;
	private String string;
	private int lastvalue;
	private int transactionId;
	private String state;

	/** Variable to access file methods */
	private FileAccessor fileAccessor;
	private File stateLogFile;
	private File outputFile;
	private FileReader fileReader, outputReader;
	private BufferedReader bufferedReader = null, outputBufferedReader = null;
	private long length;

	/**
	 * Variables to calculate the timeout of cohort
	 */
	private Date startTime;
	private Date endTime;
	private long timeOut;
	private long duration;

	/**
	 * Default constructor to initialize the variables
	 */
	public Cohort() {
		fileAccessor = new FileAccessor();
		isAborted = false;
		sentAck = false;
		recoveryDone = false;
		startComputation = false;
		recoveryCheckDone = false;

		lastvalue = 0;
		transactionId = 0;
	}

	/**
	 * A method to make thread sleep, used for the purpose of calculating
	 * timeouts
	 */
	public void makeThreadSleep() {
		Scanner input = new Scanner(System.in);
		System.out.println("Enter the seconds for which Cohort should sleep: ");
		int threadSleep = input.nextInt();

		try {
			Thread.sleep(threadSleep * 1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * A method that would be executed by the thread
	 */
	public void start() {
		try {

			// Establish a connection to the Coordinator
			cohortSocket = new Socket(coordinatorHostName, coordinatorPort);
			cohortDataInputStream = new DataInputStream(cohortSocket.getInputStream());
			cohortPrintStream = new PrintStream(cohortSocket.getOutputStream());

			// Register itself with the coordinator
			cohortPrintStream.println(StringConstants.MESSAGE_REGISTER + StringConstants.SPACE
					+ InetAddress.getLocalHost().getHostName() + StringConstants.SPACE + PORT + StringConstants.SPACE);
			cohortPrintStream.flush();
			System.out.println(StringConstants.MESSAGE_REGISTER + StringConstants.SPACE
					+ InetAddress.getLocalHost().getHostName() + StringConstants.SPACE + PORT + StringConstants.SPACE);

			// Fetch the process id assigned by the Coordinator
			readInputStream = cohortDataInputStream.readLine();
			pId = Integer.parseInt(readInputStream.split(StringConstants.SPACE)[0]);
			System.out.println("Process Id: " + pId);

			// Access the required files
			stateLogFile = new File(System.getProperty("user.dir") + "/StateInfo_Cohort" + pId);
			outputFile = new File(System.getProperty("user.dir") + "/Output_Cohort" + pId);

			while (true) {
				readInputStream = null;
				readInputStream = cohortDataInputStream.readLine();

				length = stateLogFile.length();

				/*
				 * Recovery Mechanism. It reads a State log file and the state
				 * before the failure and performs the action accordingly.
				 */
				if ((int) length != 0 && !recoveryCheckDone) {
					recoveryCheckDone = true;
					startComputation = true;

					System.out.println("Cohort" + pId + " failed and now recovery is in progress");

					fileReader = new FileReader(stateLogFile);
					bufferedReader = new BufferedReader(fileReader);
					outputReader = new FileReader(outputFile);
					outputBufferedReader = new BufferedReader(outputReader);

					state = bufferedReader.readLine();
					System.out.println("State of Cohort previous to failure " + state.split(StringConstants.SPACE)[0]);

					while ((string = outputBufferedReader.readLine()) != null) {
						transactionId = Integer.parseInt(string.split(StringConstants.SPACE)[0]) + 1;
					}

					bufferedReader.close();
					outputBufferedReader.close();

					if (!state.equalsIgnoreCase(StringConstants.STATE_Q + pId))
						lastvalue = Integer.parseInt(state.split(StringConstants.SPACE)[1]);

					// If the state is Q
					if (state.split(StringConstants.SPACE)[0].startsWith("Q" + pId) && !recoveryDone) {
						recoveryDone = true;
						System.out.println("Upon recovery, the state is Q" + pId + ". Hence, Cohort should abort");

						fileAccessor.writeToStateLogFile(stateLogFile, "");
						fileAccessor.writeToOutputFile(outputFile, transactionId + StringConstants.SPACE + "Aborted");

						System.out.println("After Recovery, transition between the states for Cohort is : q" + pId
								+ " --> a" + pId);

						System.out.println("...Cohort Terminates...");
						System.out.println();
						break;

					} else if (state.split(StringConstants.SPACE)[0].startsWith("W" + pId) && !recoveryDone) {
						// If the state is W

						recoveryDone = true;
						System.out.println("Upon recovery, the state is w" + pId + ". Hence, Cohort should abort");

						fileAccessor.writeToStateLogFile(stateLogFile, "");
						fileAccessor.writeToOutputFile(outputFile,
								transactionId + StringConstants.SPACE + lastvalue + StringConstants.SPACE + "Aborted");

						System.out.println("After Recovery, transition between the states for Cohort is : w" + pId
								+ " --> a" + pId);
						System.out.println("...Cohort Terminates...");
						System.out.println();

						break;

					} else if (state.split(StringConstants.SPACE)[0].startsWith("P" + pId) && !recoveryDone) {
						// If the state is P

						recoveryDone = true;
						System.out.println("Upon recovery, the state is P" + pId + ". Hence, Cohort should commit");

						fileAccessor.writeToStateLogFile(stateLogFile, "");
						fileAccessor.writeToOutputFile(outputFile, transactionId + StringConstants.SPACE + lastvalue
								+ StringConstants.SPACE + "Committed");

						System.out.println("After Recovery, transition between the states for Cohort is : p" + pId
								+ " --> c" + pId);
						System.out.println("...Cohort Terminates...");
						System.out.println();

						break;
					}

				} else if (!startComputation) {

					startComputation = true;
					fileAccessor.writeToStateLogFile(stateLogFile, StringConstants.STATE_Q + pId);
					startTime = new Date();

					System.out.println("Cohort is in state q");

					// To comment
					System.out.println("Enter the TIMEOUT seconds for Cohort: ");
					Scanner input = new Scanner(System.in);
					timeOut = input.nextInt() * 1000;
					System.out.println("Cohort TIMEOUT is " + timeOut);
					endTime = new Date();

					if (endTime.getTime() - startTime.getTime() > timeOut) {
						System.out.println("Cohort Timeout. Transition from q" + pId + " --> a" + pId);

						cohortPrintStream.println(" ");
						cohortPrintStream.flush();

						fileAccessor.writeToStateLogFile(stateLogFile, "");
						fileAccessor.writeToOutputFile(outputFile, transactionId + StringConstants.SPACE + "ABORTED");

						System.out.println("...Cohort Terminates...");
						System.out.println();
						break;
					}

					startTime = new Date();

					boolean hasCommunicationStarted = false;

					while ((readInputStream = cohortDataInputStream.readLine()) != null) {
						endTime = new Date();

						if (endTime.getTime() - startTime.getTime() > timeOut && !hasCommunicationStarted) {
							System.out.println("Cohort Timeout. Transition from q" + pId + " --> a" + pId);

							cohortPrintStream.println(" ");
							cohortPrintStream.flush();

							fileAccessor.writeToStateLogFile(stateLogFile, "");
							fileAccessor.writeToOutputFile(outputFile,
									transactionId + StringConstants.SPACE + "ABORTED");

							System.out.println("...Cohort Terminates...");
							System.out.println();
							break;

						} else {
							hasCommunicationStarted = true;

							// COMMIT REQ received
							if (readInputStream.split(StringConstants.SPACE)[0]
									.startsWith(StringConstants.MESSAGE_COMMIT_REQUEST)) {
								System.out.println("Cohort received COMMIT REQ for transaction ID "
										+ readInputStream.split(StringConstants.SPACE)[1] + " and value "
										+ readInputStream.split(StringConstants.SPACE)[2]);
								endTime = new Date();
								duration = endTime.getTime() - startTime.getTime();

								// Timeout happen
								if (duration > timeOut) {
									System.out.println("Cohort TIMEOUT. Transition from q" + pId + " --> a" + pId);

									fileAccessor.writeToStateLogFile(stateLogFile, "");
									fileAccessor.writeToOutputFile(outputFile,
											readInputStream.split(StringConstants.SPACE)[1] + StringConstants.SPACE
													+ readInputStream.split(StringConstants.SPACE)[2]
													+ StringConstants.SPACE + "Aborted");

									System.out.println("...Cohort Terminates...");
									System.out.println();

									/* System.exit(0); */

									break;

								} else {
									/*
									 * fileAccessor.writeToStateLogFile(
									 * stateLogFile, StringConstants.STATE_W +
									 * pId + StringConstants.SPACE +
									 * readInputStream.split(StringConstants.
									 * SPACE)[ 2]);
									 * 
									 */

									System.out.println("Cohort " + pId + " received COMMIT_REQUEST from Coordinator");

									System.out.println("Press N to send ABORT or any other key to send AGREED...");
									Scanner input1 = new Scanner(System.in);
									choice = input1.nextLine();

									if (choice.equalsIgnoreCase("N")) {
										isAborted = true;

										fileAccessor.writeToStateLogFile(stateLogFile, "");
										fileAccessor.writeToOutputFile(outputFile,
												readInputStream.split(StringConstants.SPACE)[1] + StringConstants.SPACE
														+ readInputStream.split(StringConstants.SPACE)[2]
														+ StringConstants.SPACE + "Aborted");

										cohortPrintStream.println(StringConstants.MESSAGE_ABORT);
										cohortPrintStream.flush();

										System.out.println("Cohort " + pId + "sent ABORT to the Coordinator");
										System.out.println("Transition between the states for Cohort is : q" + pId
												+ " --> a" + pId);
										System.out.println("...Cohort Terminates...");
										System.out.println();

										break;
									} else {
										fileAccessor.writeToStateLogFile(stateLogFile,
												StringConstants.STATE_W + pId + StringConstants.SPACE
														+ readInputStream.split(StringConstants.SPACE)[2]);

										startTime = new Date();
										cohortPrintStream.println(StringConstants.MESSAGE_AGREED);
										cohortPrintStream.flush();

										System.out.println("Cohort " + pId + " sent AGREED to the Coordinator");
										System.out.println("Transition between the states for Cohort is : q" + pId
												+ " --> w" + pId);
									}
								}
							}

							// Prepare Message received
							if (readInputStream.split(StringConstants.SPACE)[0].equals(StringConstants.MESSAGE_PREPARE)
									&& !sentAck) {
								endTime = new Date();
								duration = endTime.getTime() - startTime.getTime();

								System.out.println("Cohort " + pId + " received PREPARE from the Coordinator");

								if (duration > timeOut) {
									System.out.println("Cohort TIMEOUT");

									cohortPrintStream.println(" ");
									cohortPrintStream.flush();

									fileAccessor.writeToStateLogFile(stateLogFile, "");
									fileAccessor.writeToOutputFile(outputFile,
											readInputStream.split(StringConstants.SPACE)[1] + StringConstants.SPACE
													+ readInputStream.split(StringConstants.SPACE)[2]
													+ StringConstants.SPACE + "ABORTED");

									System.out.println(
											"Transition between the states for Cohort is : w" + pId + " --> a" + pId);
									System.out.println("...Cohort Terminates...");
									System.out.println();

									break;

								} else {
									fileAccessor.writeToStateLogFile(stateLogFile, StringConstants.STATE_P + pId
											+ StringConstants.SPACE + readInputStream.split(StringConstants.SPACE)[2]);

									// Send ACK to coordinator
									// makeThreadSleep();

									if (Integer.parseInt(readInputStream.split(StringConstants.SPACE)[1]) % 3 == 0) {
										Thread.sleep(21000);
									}
									cohortPrintStream.println("ACK");
									cohortPrintStream.flush();
									sentAck = true;

									System.out.println("Cohort " + pId + " sent ACK to the Coordinator");
									startTime = new Date();
								}
							}

							// Commit Message received
							if (readInputStream.split(StringConstants.SPACE)[0]
									.equals(StringConstants.MESSAGE_COMMIT)) {
								endTime = new Date();
								duration = endTime.getTime() - startTime.getTime();

								if (Integer.parseInt(readInputStream.split(StringConstants.SPACE)[2]) % 4 == 0) {
									System.out.println("For every fourth transaction, Cohort FAILS.");
									Thread.sleep(10000);

								} else {
									if (duration > timeOut) {
										System.out.println(
												"Cohort TIMEOUT, transition between the states for Cohort is : p" + pId
														+ " --> c" + pId);

									} else {
										System.out
												.println("After COMMIT, transition between the states for Cohort is : p"
														+ pId + " --> c" + pId);
									}

									fileAccessor.writeToStateLogFile(stateLogFile, "");
									fileAccessor.writeToOutputFile(outputFile,
											readInputStream.split(StringConstants.SPACE)[1] + StringConstants.SPACE
													+ readInputStream.split(StringConstants.SPACE)[2]
													+ StringConstants.SPACE + "Committed");

									System.out.println();
									System.out.println("...Cohort Terminates...");
									System.err.println();

									break;
								}

							}

							// Abort Message received
							if (readInputStream.split(StringConstants.SPACE)[0].equals(StringConstants.MESSAGE_ABORT)
									&& !isAborted) {
								isAborted = true;

								System.out.println("Cohort " + pId + " received ABORT from the Coordinator");

								fileReader = new FileReader(stateLogFile);
								bufferedReader = new BufferedReader(fileReader);
								state = bufferedReader.readLine().split(StringConstants.SPACE)[0];

								fileAccessor.writeToStateLogFile(stateLogFile, "");
								fileAccessor.writeToOutputFile(outputFile,
										readInputStream.split(StringConstants.SPACE)[1] + StringConstants.SPACE
												+ readInputStream.split(StringConstants.SPACE)[2]
												+ StringConstants.SPACE + "Aborted");

								System.out.println(
										"In THREE PHASE PROTOCOL, after ABORT received, transition between the states for Cohort is : "
												+ state + " --> a" + pId);

								System.out.println("...Cohort Terminates...");
								System.out.println();
								break;
							}
						}
					}
				}
			}
		} catch (

		UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Getters and Setters to access the private variables
	 */
	public int getMaxProcess() {
		return maxCohort;
	}

	public void setMaxProcess(int maxProcess) {
		this.maxCohort = maxProcess;
	}

	public String getCoordinatorHostName() {
		return coordinatorHostName;
	}

	public void setCoordinatorHostName(String coordinatorHostName) {
		this.coordinatorHostName = coordinatorHostName;
	}
}
