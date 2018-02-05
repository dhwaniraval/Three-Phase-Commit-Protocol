package com.aos.utility;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.aos.model.StringConstants;

public class FileAccessor {

	private FileWriter stateFileWriter = null;
	private BufferedWriter bufferedWriter = null;
	private FileWriter outputFileWriter = null;
	private BufferedWriter outputBufferedFileWriter = null;
	private FileReader outputReader;
	private BufferedReader outputBufferedReader = null;
	private String string;

	/**
	 * Method to write into the state log file
	 */
	public void writeToStateLogFile(File stateFile, String state) {
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
	 * A method to write into the output file
	 */
	public void writeToOutputFile(File outputFile, String newOutput) {

		try {
			outputReader = new FileReader(outputFile);
			outputBufferedReader = new BufferedReader(outputReader);
			List<String> previousEntries = new ArrayList<>();

			while ((string = outputBufferedReader.readLine()) != null) {
				StringBuilder stringB = new StringBuilder();

				for (String string : string.split(StringConstants.SPACE)) {
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
			if (newOutput == "") {
				System.out.println(
						"The state is either ABORTED or COMMITTED. Hence remove the enteries from the state log file");
			} else
				System.out.println("The state written in the state log file is : " + newOutput);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
