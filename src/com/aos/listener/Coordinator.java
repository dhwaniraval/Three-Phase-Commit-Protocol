package com.aos.listener;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import com.aos.model.Node;
import com.aos.model.StringConstants;
import com.aos.utility.SharedDataAmongCoordThreads;

/**
 * @author dsr170230
 */
public class Coordinator {
	/**
	 * Variables required for establishing connections among processes and
	 * coordinator
	 */
	private int PORT = 9001;
	private String readInputStream;
	private static int assignedProcessId;
	private int variable;

	private DataInputStream inputStream = null;
	private Socket socket = null;
	private ServerSocket coordinatorListenSocket = null;

	private static HashMap<Integer, Socket> connectionsToCoordinator;

	/**
	 * Variables to store information obtained from Config file
	 */
	private int interval;
	private int terminate;
	private int maxProcess;
	private String coordinatorHostName;

	/**
	 * Variable arrays to store the process id, host name and port no of all the
	 * processes in the topology
	 */
	private static int[] portNoArray;
	private static int[] processIdArray;
	private static String[] hostNameArray;

	/**
	 * Variables required for various computations
	 */
	private String[] breakReadInputStream;
	private Node processNode;

	private SharedDataAmongCoordThreads data;

	/**
	 * Default constructor that will initialize many variables
	 */
	public Coordinator() {
		assignedProcessId = 1;
		connectionsToCoordinator = new HashMap<>();
		data = new SharedDataAmongCoordThreads();
	}

	/**
	 * A method that will accept incoming connections from various processes,
	 * will store the information of all the processes and then will act as a
	 * normal process once all the READY messages have been received
	 */
	public void start() {
		initialzeArray();

		try {
			// Start server at the given PORT
			coordinatorListenSocket = new ServerSocket(PORT);
			coordinatorListenSocket.setReuseAddress(true);
			coordinatorListenSocket.setSoTimeout(1000 * 60 * 60);

			System.out.println("Coordinator Process started at port: " + PORT);

			// Store the information about coordinator itself in its arrays
			processIdArray[assignedProcessId - 1] = assignedProcessId;
			hostNameArray[assignedProcessId - 1] = InetAddress.getLocalHost().getHostName();
			portNoArray[assignedProcessId - 1] = PORT;

			// Accept incoming connections from cohorts
			while (true) {
				assignedProcessId++;
				socket = coordinatorListenSocket.accept();
				connectionsToCoordinator.put(assignedProcessId, socket);

				inputStream = new DataInputStream(socket.getInputStream());

				readInputStream = inputStream.readLine();

				// If received message is REGISTER
				if (readInputStream.startsWith(StringConstants.MESSAGE_REGISTER)) {
					breakReadInputStream = readInputStream.split(StringConstants.SPACE);

					// Check if the id is already present in the array
					if (!searchTable(assignedProcessId)) {

						// Print the received request from the process
						System.out.println("Received: " + readInputStream);

					}

					/*
					 * After insertion, create a new thread other computations
					 * than the register would be handled
					 */
					new CoordinatorServerHandler(socket, maxProcess, inputStream, assignedProcessId, data).start();

					/*
					 * Once all cohorts register, enale the coordinator
					 * computation
					 */
					if (assignedProcessId == maxProcess + 1) {

						CoordinatorClientHandler c = new CoordinatorClientHandler(variable, data);
						c.start();
						break;
					}
				}

			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * A method to initialize the arrays where in process id, host name and port
	 * no of all the processes in the topology is stored
	 */
	private void initialzeArray() {
		if (maxProcess != 0) {
			processIdArray = new int[maxProcess + 1];
			hostNameArray = new String[maxProcess + 1];
			portNoArray = new int[maxProcess + 1];
		}
	}

	/**
	 * A method to search whether the generated assigned Id is already present
	 * in the array
	 */
	private boolean searchTable(int processId) {
		for (int i = 0; i < maxProcess; i++) {
			if (processIdArray[i] == processId)
				return true;
		}
		return false;
	}

	/**
	 * Getters and Setters for private variables
	 */
	public String getHostName() {
		return coordinatorHostName;
	}

	public void setHostName(String hostName) {
		this.coordinatorHostName = hostName;
	}

	public int getMaxProcess() {
		return maxProcess;
	}

	public void setMaxProcess(int maxProcess) {
		this.maxProcess = maxProcess;
	}

	public int getTerminate() {
		return terminate;
	}

	public void setTerminate(int terminate) {
		this.terminate = terminate;
	}

	public int getInterval() {
		return interval;
	}

	public void setInterval(int interval) {
		this.interval = interval;
	}
}
