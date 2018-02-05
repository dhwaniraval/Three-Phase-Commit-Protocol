package com.aos.listener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

import com.aos.model.StringConstants;
import com.aos.utility.SharedDataAmongCoordThreads;

public class CoordinatorClientHandler extends Thread {

	private int variable;
	private SharedDataAmongCoordThreads sharedData;

	private Date startTime;
	private Date endTime;

	private String line = null;
	private FileReader fileReader, outputReader;
	private BufferedReader bufferedReader = null, outputBufferedReader = null;
	private FileWriter stateFileWriter = null;
	private BufferedWriter bufferedWriter = null;

	private int transactionId = 0;
	private File outputFile;
	private File stateFile;
	private String state;

	CoordinatorClientHandler(int variable, SharedDataAmongCoordThreads data) {
		this.variable = variable;
		this.sharedData = data;
	}

	@Override
	public void start() {

		stateFile = new File(System.getProperty("user.dir") + "/StateInfo_Coordinator");
		outputFile = new File(System.getProperty("user.dir") + "/Output_Coordinator");

		Scanner input = new Scanner(System.in);

		long length = stateFile.length();

		if ((int) length == 0) {
			line = StringConstants.STATE_Q1 + StringConstants.SPACE;

			length = outputFile.length();
			if ((int) length == 0) {
				transactionId = 1;
			} else {
				try {
					outputReader = new FileReader(outputFile);
					outputBufferedReader = new BufferedReader(outputReader);

					String s;
					while ((s = outputBufferedReader.readLine()) != null) {
						transactionId = Integer.parseInt(s.split(StringConstants.SPACE)[0]) + 1;
						sharedData.setTransactionID(transactionId);
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
			try {
				stateFileWriter = new FileWriter(stateFile);
				bufferedWriter = new BufferedWriter(stateFileWriter);

				bufferedWriter.append(line);

				startTime = new Date();

				sharedData.setRequestInitiated(true);

				System.out.println("Enter an integer of your choice to commit: ");
				variable = input.nextInt();

				System.out.println("Entered value to commit is " + variable);
				bufferedWriter.append(Integer.toString(variable) + " ");

				endTime = new Date();

				bufferedWriter.flush();
				bufferedWriter.close();

				long duration = endTime.getTime() - startTime.getTime();
				if (duration <= 60000) {
					sharedData.setCommitedValue(variable);
					sharedData.setCommitMade(true);

					Thread.sleep(500);
				} else {
					sharedData.setQ1TimeOut(true);
				}

			} catch (IOException e1) {
				e1.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		} else {

			try {
				System.out.println("Coordinator failed and now recovery is in progress");

				fileReader = new FileReader(stateFile);
				bufferedReader = new BufferedReader(fileReader);

				outputReader = new FileReader(outputFile);
				outputBufferedReader = new BufferedReader(outputReader);

				state = bufferedReader.readLine();
				System.out.println("State of Coordinator previous to failure " + state.split(StringConstants.SPACE)[0]);

				String s;
				int lastvalue = 0;

				while ((s = outputBufferedReader.readLine()) != null) {
					transactionId = Integer.parseInt(s.split(StringConstants.SPACE)[0]) + 1;
				}

				lastvalue = Integer.parseInt(state.split(StringConstants.SPACE)[1]);

				sharedData.setCommitedValue(lastvalue);
				sharedData.setTransactionID(transactionId);

				if (state.split(StringConstants.SPACE)[0].startsWith("Q1")
						|| state.split(StringConstants.SPACE)[0].startsWith("W1")) {

					sharedData.setAbortAfterRecovery(true);

					System.out.println("Coordinator should abort.");

					Thread.sleep(500);
				} else {
					sharedData.setCommitAfterRecovery(true);

					System.out.println("Coordinator should Commit");

					Thread.sleep(500);
				}

			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}