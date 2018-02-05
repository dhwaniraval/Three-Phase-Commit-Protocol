package com.aos.model;

public class Process {
	public String coordinatorName;
	public int maxCohort;
	public int terminate;

	public String getCoordinatorName() {
		return coordinatorName;
	}

	public void setCoordinatorName(String coordinatorName) {
		this.coordinatorName = coordinatorName;
	}

	public int getMaxCohort() {
		return maxCohort;
	}

	public void setMaxCohort(int maxCohort) {
		this.maxCohort = maxCohort;
	}

	public int getTerminate() {
		return terminate;
	}

	public void setTerminate(int terminate) {
		this.terminate = terminate;
	}

}
