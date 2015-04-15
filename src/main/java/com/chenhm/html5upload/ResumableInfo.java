package com.chenhm.html5upload;

import java.util.HashSet;

public class ResumableInfo {

	public String resumableChunkSize;
	public String resumableTotalSize;
	public String resumableIdentifier;
	public String resumableFilename;
	public String resumableRelativePath;

	public static class ResumableChunkNumber {
		public ResumableChunkNumber(int number) {
			this.number = number;
		}

		public int number;

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ResumableChunkNumber ? ((ResumableChunkNumber) obj).number == this.number
					: false;
		}

		@Override
		public int hashCode() {
			return number;
		}
	}

	// Chunks uploaded
	public HashSet<ResumableChunkNumber> uploadedChunks = new HashSet<ResumableChunkNumber>();

	public boolean vaild() {
		if (toLong(resumableChunkSize, -1) < 0
				|| toLong(resumableTotalSize, -1) < 0
				|| isEmpty(resumableIdentifier) || isEmpty(resumableFilename)
				|| isEmpty(resumableRelativePath)) {
			return false;
		} else {
			return true;
		}
	}

	public static boolean isEmpty(String value) {
		return value == null || "".equals(value);
	}

    public static long toLong(String value, long def) {
        if (isEmpty(value)) {
            return def;
        }

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return def;
        }
    }

	public boolean checkIfUploadFinished() {
		// check if upload finished
		int count = (int) Math.ceil(((double) toLong(resumableTotalSize, -1))
				/ ((double) toLong(resumableChunkSize, -1)));
		for (int i = 1; i < count; i++) {
			if (!uploadedChunks.contains(new ResumableChunkNumber(i))) {
				return false;
			}
		}

		return true;
	}
}
