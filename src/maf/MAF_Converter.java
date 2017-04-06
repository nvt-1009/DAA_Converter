package maf;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import daa.writer.DAA_Writer;
import hits.Hit;
import io.FastAQ_Reader;
import util.LineCounter;
import util.SparseString;

public class MAF_Converter {

	private int maxProgress, lastProgress = 0;
	private AtomicInteger progress = new AtomicInteger();

	private CountDownLatch latch;
	private ExecutorService executor;

	public void run(File daaFile, File mafFile, File queryFile, int cores) {

		long time = System.currentTimeMillis();
		System.out.println("MAF2DAA converter by Benjamin Albrecht");

		this.executor = Executors.newFixedThreadPool(cores);
		MAF_Header headerInfo = new MAF_Header(mafFile);
		headerInfo.load();

		long numOfLines = LineCounter.run(mafFile);
		long chunk = (long) Math.ceil((double) numOfLines / (double) cores);

		// processing maf file
		System.out.println("Processing maf-file: " + mafFile.getAbsolutePath());
		maxProgress = (int) numOfLines;
		ConcurrentSkipListSet<SubjectEntry> subjectInfoSet = new ConcurrentSkipListSet<SubjectEntry>();
		ConcurrentSkipListSet<Long> batchSet = new ConcurrentSkipListSet<Long>();
		ArrayList<Thread> processThreads = generateProcessThreads(mafFile, chunk, subjectInfoSet, batchSet);
		runInParallel(processThreads);
		ArrayList<Object[]> subjectInfo = new ArrayList<Object[]>();
		Iterator<SubjectEntry> it = subjectInfoSet.iterator();
		while (it.hasNext()) {
			SubjectEntry e = it.next();
			Object[] subject = { e.getName(), e.getLength() };
			subjectInfo.add(subject);
		}
		reportFinish();

		// parsing read information
		System.out.println("Processing read-file: " + queryFile.getAbsolutePath());
		ArrayList<Object[]> readInfos = FastAQ_Reader.read(queryFile);

		// writing header of daa file
		DAA_Writer daaWriter = new DAA_Writer(daaFile);
		daaWriter.writeHeader(headerInfo.getDbSeqs(), headerInfo.getDbLetters(), headerInfo.getGapOpen(), headerInfo.getGapExtend(),
				headerInfo.getK(), headerInfo.getLambda());

		// writing hits into daa file
		maxProgress = (int) numOfLines;
		progress.set(0);
		System.out.println("Writing into daa-file: " + daaFile.getAbsolutePath());
		ArrayList<Thread> batchReaders = new ArrayList<Thread>();
		for (long filePointer : batchSet)
			batchReaders.add(new BatchReader(filePointer, mafFile, subjectInfo));
		ArrayList<Hit> hits = new ArrayList<Hit>();
		for (int i = 0; i < readInfos.size(); i++) {

			Object[] readInfo = readInfos.get(i);

			// reading-out hits in parallel
			for (Thread reader : batchReaders)
				((BatchReader) reader).setReadName(readInfo);
			runInParallel(batchReaders);

			// storing hits
			for (Thread reader : batchReaders) {
				for (MAF_Hit mafHit : ((BatchReader) reader).getHits())
					hits.add(new Hit(mafHit));
			}

			// writing hits into daa file
			if (hits.size() > 10000 || i == readInfos.size() - 1) {
				daaWriter.writeHits(hits);
				hits.clear();
			}

		}

		// writing subject info into daa file
		daaWriter.writeEnd(subjectInfo);

		reportFinish();

		executor.shutdown();

		long runtime = (System.currentTimeMillis() - time) / 1000;
		System.out.println("Runtime: " + (runtime / 60) + "min " + (runtime % 60) + "s");

	}

	private void reportProgress(int delta) {
		progress.getAndAdd(delta);
		int p = ((int) ((((double) progress.get() / (double) maxProgress)) * 100) / 10) * 10;
		if (p > lastProgress && p < 100) {
			lastProgress = p;
			System.out.print(p + "% ");
		}
	}

	private void reportFinish() {
		progress.set(0);
		lastProgress = 0;
		System.out.print(100 + "%\n");
	}

	public class BatchReader extends Thread {

		private ArrayList<Object[]> subjectInfo;
		private RandomAccessFile raf;
		private Object[] readInfo;
		private ArrayList<MAF_Hit> hits;

		private int parsedLines = 0;
		private boolean endOfBatchReached = false;
		private MAF_Hit lastParsedHit;
		private byte[] buffer = new byte[1024 * 1024];
		private int readChars;
		private int last_i = 0;

		public BatchReader(long filePointer, File mafFile, ArrayList<Object[]> subjectInfo) {
			try {
				raf = new RandomAccessFile(mafFile, "r");
				raf.seek(filePointer);
				readChars = raf.read(buffer);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			this.subjectInfo = subjectInfo;
		}

		public void run() {

			hits = new ArrayList<MAF_Hit>();
			if (lastParsedHit != null) {
				if (!lastParsedHit.getReadName().equals(readInfo[0].toString())) {
					latch.countDown();
					return;
				}
				lastParsedHit.setReadInfo(readInfo);
				hits.add(lastParsedHit);
				lastParsedHit = null;
			}

			if (endOfBatchReached) {
				latch.countDown();
				return;
			}

			try {

				StringBuffer line = new StringBuffer();
				String[] lineTriple = new String[3];
				for (int i = last_i; i < readChars; i++) {

					char c = (char) buffer[i];

					if (c != '\n')
						line = line.append(c);
					else {

						parsedLines++;
						if (parsedLines > 0 && parsedLines % 100 == 0)
							reportProgress(100);

						last_i = i + 1;
						String l = line.toString();
						line = new StringBuffer();

						if (l.startsWith("#")) {
							endOfBatchReached = true;
							break;
						}

						if (l.startsWith("s") && lineTriple[1] != null) {

							lineTriple[2] = l;

							MAF_Hit hit = new MAF_Hit(lineTriple, readInfo, subjectInfo);
							if (!hit.getReadName().equals(readInfo[0].toString())) {
								lastParsedHit = hit;
								break;
							}
							hit.setReadInfo(readInfo);
							if (hit.makesSense())
								hits.add(hit);

							lineTriple = new String[3];

						}

						else if (l.startsWith("a"))
							lineTriple[0] = l;

						else if (l.startsWith("s") && lineTriple[1] == null)
							lineTriple[1] = l;

					}

					if (i == readChars - 1) {
						i = -1;
						readChars = raf.read(buffer);
					}

				}
			} catch (

			Exception e) {
				e.printStackTrace();
			}

			latch.countDown();

		}

		public void setReadName(Object[] readInfo) {
			this.readInfo = readInfo;
		}

		public ArrayList<MAF_Hit> getHits() {
			return hits;
		}

	}

	public void runInParallel(ArrayList<Thread> threads) {
		latch = new CountDownLatch(threads.size());
		for (Thread t : threads)
			executor.execute(t);
		try {
			latch.await();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public class ProcessThread extends Thread {

		private File mafFile;
		private long startPos, chunkSize;
		private ConcurrentSkipListSet<SubjectEntry> subjectInfo_Set;
		private ConcurrentSkipListSet<Long> batchSet;

		public ProcessThread(File mafFile, long startPos, long chunkSize, ConcurrentSkipListSet<SubjectEntry> subjectInfo_Set,
				ConcurrentSkipListSet<Long> batchSet) {
			this.mafFile = mafFile;
			this.startPos = startPos;
			this.chunkSize = chunkSize;
			this.subjectInfo_Set = subjectInfo_Set;
			this.batchSet = batchSet;
		}

		public void run() {

			try {

				RandomAccessFile raf = new RandomAccessFile(mafFile, "r");
				raf.seek(startPos);

				byte[] buffer = new byte[1024 * 1024];
				int readChars = 0, colNumber = 0, parsedLines = 0;
				boolean b1 = false, b2 = false, b3 = false, b4 = false;
				StringBuffer buf = new StringBuffer();
				StringBuffer line = new StringBuffer();
				Object[] subject = new Object[2];
				while ((readChars = raf.read(buffer)) != -1) {

					if (parsedLines > chunkSize)
						break;

					for (int i = 0; i < readChars; i++) {

						char c = (char) buffer[i];
						if (c == '\n') {
							parsedLines++;
							if (parsedLines % 100 == 0)
								reportProgress(100);
						}
						if (parsedLines > chunkSize)
							break;

						switch (c) {
						case '\n':
							if (b2) {
								if (subject[0] != null && subject[1] != null)
									subjectInfo_Set.add(new SubjectEntry((SparseString) subject[0], (int) subject[1]));
								b2 = false;
							}
							if (b4) {
								batchSet.add(raf.getFilePointer() - (readChars - i));
								b4 = false;
							}
							buf = new StringBuffer();
							line = new StringBuffer();
							colNumber = 0;
							break;
						case ' ':
							if (i > 0 && (char) buffer[i - 1] != ' ') {
								String content = buf.toString();
								if (colNumber == 0 && content.equals("a"))
									b1 = true;
								if (colNumber == 0 && content.equals("s") && b1) {
									b1 = false;
									b2 = true;
									subject = new Object[2];
								}
								if (colNumber == 0 && content.equals("#"))
									b3 = true;
								if (b2 && colNumber == 1)
									subject[0] = new SparseString(buf.toString());
								if (b3 && colNumber == 1 && buf.toString().equals("batch")) {
									subject[0] = new SparseString(buf.toString());
									b3 = false;
									b4 = true;
								}
								if (b2 && colNumber == 5)
									subject[1] = Integer.parseInt(buf.toString());
								buf = new StringBuffer();
								line = line.append(c);
								colNumber++;
							}
							break;
						default:
							buf = buf.append(c);
							line = line.append(c);
						}

					}

				}

				raf.close();

			} catch (Exception e) {
				e.printStackTrace();
			}

			latch.countDown();

		}

	}

	public ArrayList<Thread> generateProcessThreads(File file, long chunk, ConcurrentSkipListSet<SubjectEntry> subjectInfo_Set,
			ConcurrentSkipListSet<Long> batchSet) {

		ArrayList<Thread> processThreads = new ArrayList<Thread>();
		try {
			InputStream is = new BufferedInputStream(new FileInputStream(file));
			try {
				byte[] c = new byte[1024];
				int count = 0;
				int readChars = 0;
				long filePointer = 0;
				processThreads.add(new ProcessThread(file, filePointer, chunk, subjectInfo_Set, batchSet));
				while ((readChars = is.read(c)) != -1) {
					for (int i = 0; i < readChars; ++i) {
						filePointer++;
						if (c[i] == '\n') {
							count++;
							if (count % (chunk + 1) == 0)
								processThreads.add(new ProcessThread(file, filePointer, chunk, subjectInfo_Set, batchSet));
						}
					}
				}
				return processThreads;
			} finally {
				is.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return processThreads;

	}

	public class SubjectEntry implements Comparable<SubjectEntry> {

		private SparseString name;
		private int length;

		public SubjectEntry(SparseString name, int length) {
			this.name = name;
			this.length = length;
		}

		@Override
		public int compareTo(SubjectEntry o) {
			return name.compareTo(o.getName());
		}

		public SparseString getName() {
			return name;
		}

		public int getLength() {
			return length;
		}

	}

}