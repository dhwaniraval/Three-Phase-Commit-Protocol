package com.aos.model;
/**
 * A class that stores the information pertaining to a node in the topology
 */
public class Node {
	private int processId;
	private String hostName;
	private int portNo;

	public int getProcessId() {
		return processId;
	}

	public void setProcessId(int processId) {
		this.processId = processId;
	}

	public String getHostName() {
		return hostName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

	public int getPortNo() {
		return portNo;
	}

	public void setPortNo(int portNo) {
		this.portNo = portNo;
	}
}
