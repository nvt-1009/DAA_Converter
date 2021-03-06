/*
 * Copyright 2017 Benjamin Albrecht
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package hits;

import java.util.ArrayList;

import maf.MAF_Hit;

public class Hit {

	public enum FrameDirection {
		POSITIVE, NEGATIVE
	}

	private String readName;
	private int totalQueryLenth;
	private byte[] packedQuerySequence;
	private FrameDirection frame;
	private int rawScore, ref_start, query_start;

	private int subjectID = -1;

	public ArrayList<Byte> editOperations;

	public Hit(FrameDirection frame, int rawScore, int ref_start, int query_start, int subjectID, int readID, ArrayList<Byte> editOperations) {
		this.frame = frame;
		this.rawScore = rawScore;
		this.ref_start = ref_start;
		this.query_start = query_start;
		this.subjectID = subjectID;
		this.editOperations = editOperations;
	}

	public Hit(int ref_start, int ref_end, int bitScore, int rawScore, long file_pointer, Integer accessPoint, int query_start, int ref_length,
			int query_length, int subjectID) {
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

	public Hit(MAF_Hit mafHit) {
		this.frame = mafHit.getFrameDir();
		this.rawScore = mafHit.getRawScore();
		this.ref_start = mafHit.getRefStart();
		this.query_start = mafHit.getQueryStart();
		this.subjectID = mafHit.getSubjectID();
		this.editOperations = mafHit.getEditOperations();
		this.readName = mafHit.getReadName();
		this.packedQuerySequence = mafHit.getPackedQuerySequence();
		this.totalQueryLenth = mafHit.getTotalQueryLength();
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

	public String getReadName() {
		return readName;
	}

	public int getTotalQueryLenth() {
		return totalQueryLenth;
	}

	public byte[] getPackedQuerySequence() {
		return packedQuerySequence;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Hit) {
			Hit h = (Hit) o;
			if (h.getQuery_start() == query_start && h.getSubjectID() == subjectID && h.getReadName().equals(readName) && h.getRawScore() == rawScore
					&& h.getRef_start() == ref_start && h.getEditOperations() != null && editOperations != null
					&& h.getEditOperations().size() == editOperations.size()) {
				for (int i = 0; i < editOperations.size(); i++) {
					if (editOperations.get(i) != h.getEditOperations().get(i))
						return false;
				}
				return true;
			}
		}
		return false;
	}

	// FOR DEBUGGING ***********************************

	public void print(String prefix) {
		System.out.println(prefix + " " + "\tQB:[" + query_start + ", ? ]\tRB:[" + ref_start + ", ? ]\tRS: " + rawScore + "\tFR: " + frame);
	}

	public String toString() {
		return ("\tQB:[" + query_start + ", ? ]\tRB:[" + ref_start + ", ? ]\tRS: " + rawScore + "\tFR: " + frame);
	}

}
