package hits;

import java.util.ArrayList;

public class Hit {

	public enum FrameDirection {
		POSITIVE, NEGATIVE
	}

	private FrameDirection frame;
	private int rawScore, ref_start, query_start;

	private int subjectID = -1;
	private int readID = -1;

	public ArrayList<Byte> editOperations;

	public Hit(FrameDirection frame, int rawScore, int ref_start, int query_start, int subjectID, int readID, ArrayList<Byte> editOperations) {
		this.frame = frame;
		this.rawScore = rawScore;
		this.ref_start = ref_start;
		this.query_start = query_start;
		this.subjectID = subjectID;
		this.readID = readID;
		this.editOperations = editOperations;
	}

	public Hit(int id, int ref_start, int ref_end, int bitScore, int rawScore, long file_pointer, Integer accessPoint, int query_start,
			int ref_length, int query_length, int subjectID) {
		this.ref_start = new Integer(ref_start);
		this.query_start = new Integer(query_start);
		this.rawScore = new Integer(rawScore);
		this.subjectID = subjectID;
	}

	public Hit(Hit h) {
		this.ref_start = new Integer(h.getRef_start());
		this.rawScore = new Integer(h.getRawScore());
		this.query_start = new Integer(h.getQuery_start());
		this.subjectID = new Integer(h.getSubjectID());
		this.frame = h.getFrame();
	}

	public int getRef_start() {
		return ref_start;
	}

	public int getRawScore() {
		return rawScore;
	}

	public int getQuery_start() {
		return query_start;
	}

	public void setQuery_start(int qStart) {
		this.query_start = qStart;
	}

	public FrameDirection getFrame() {
		return frame;
	}

	public void setFrame(FrameDirection frame) {
		this.frame = frame;
	}

	public void setFrame(int frame) {
		this.frame = frame > 0 ? FrameDirection.POSITIVE : FrameDirection.NEGATIVE;
	}

	public int getSubjectID() {
		return subjectID;
	}

	public ArrayList<Byte> getEditOperations() {
		return editOperations;
	}

	public int getReadID() {
		return readID;
	}

	// FOR DEBUGGING ***********************************

	public void print(String prefix) {
		System.out.println(prefix + " " + "\tQB:[" + query_start + ", ? ]\tRB:[" + ref_start + ", ? ]\tRS: " + rawScore
				+ "\tFR: " + frame);
	}

	public String toString() {
		return ("\tQB:[" + query_start + ", ? ]\tRB:[" + ref_start + ", ? ]\tRS: " + rawScore + "\tFR: " + frame);
	}

}