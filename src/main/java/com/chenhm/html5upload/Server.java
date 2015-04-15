package com.chenhm.html5upload;

import static spark.Spark.*;
import static java.lang.System.out;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;

import javax.servlet.http.HttpServletResponse;

import spark.Response;

public class Server {
	private static final String staticFileLocation = "/public/";

	private static void help() {
		out.println("Usage: java -jar html5upload.jar LISTEN_PORT UPLOAD_PATH user:pwd");
	}

	private static Map<String, String> mime = new HashMap<String, String>();
	static {
		mime.put("html", "text/html");
		mime.put("js", "text/javascript");
		mime.put("txt", "text/plain");
		mime.put("css", "text/css");
		mime.put("xml", "text/xml");
		mime.put("jpg", "image/jpeg");
		mime.put("png", "image/png");
		mime.put("svg", "image/svg+xml");
		mime.put("json", "application/json");
		mime.put("zip", "application/zip");
	}

	private static String getMIME(String ext) {
		return mime.get(ext);
	}

	private static String lsdir(File dir, String path, Response resp)
			throws IOException {
		resp.type("text/html");

		path = path.replaceAll("\\/$", "");
		StringBuilder sb = new StringBuilder();
		for (File file : dir.listFiles()) {
			sb.append("<li><a href=\"" + path + "/" + file.getName() + "\"");
			if (file.isDirectory()) {
				sb.append(" class=\"dir\"");
			}
			sb.append(">" + file.getName());
			if (file.isFile()) {
				sb.append("(" + formatSize(file.length()) + ")");
			}
			sb.append("</a></li>");
		}
		String htmlTemplate = IOUtils.toString(Server.class.getResourceAsStream(staticFileLocation + "index.html"));

		Map<String, String> map = new HashMap<String, String>();
		map.put("fileList", sb.toString());
		return format(htmlTemplate, map);
	}

	private static String formatSize(long size) {
		DecimalFormat df = new DecimalFormat("#,##0.0");
		if (size < 1024) {
			return size + "bytes";
		} else if (size < 1024 * 1024) {
			return df.format(size / 1024.0) + "KB";
		} else if (size < 1024 * 1024 * 1024) {
			return df.format(size / 1024.0 / 1024.0) + "MB";
		} else {
			return df.format(size / 1024.0 / 1024.0 / 1024.0) + "GB";
		}
	}
	
	private static void log(String msg){
		SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		out.println(sdf2.format(new Date()) + " " + msg);
	}

	private static String format(String htmlTemplate, Map<String, String> map) {
		Pattern p = Pattern.compile("#\\{(.*?)\\}");
		Matcher m = p.matcher(htmlTemplate);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String key = m.group(1);
			m.appendReplacement(sb, map.get(key));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private static void outFile(InputStream is, String name, Response resp)
			throws IOException {
		Pattern p = Pattern.compile("\\.([^\\s\\.]+)$");
		Matcher m = p.matcher(name);
		if (m.find()) {
			String ext = m.group(1);
			String mime = getMIME(ext);
			if (mime != null)
				resp.type(mime);
		}
		IOUtils.copy(is, resp.raw().getOutputStream());
		log("GET "+name);
	}

	private static void outFile(File f, String name, Response resp)
			throws IOException {
		resp.header("Content-Length", String.valueOf(f.length()));
		outFile(new java.io.FileInputStream(f), name, resp);
	}

	private static ResumableInfo getResumableInfo(Map<String, String> request) throws Exception {
		String resumableChunkSize = request.get("resumableChunkSize");
		String resumableTotalSize = request.get("resumableTotalSize");
		String resumableIdentifier = request.get("resumableIdentifier");
		String resumableFilename = request.get("resumableFilename");
		String resumableRelativePath = request.get("resumableRelativePath");
		// Here we add a ".temp" to every upload file to indicate NON-FINISHED

		ResumableInfoStorage storage = ResumableInfoStorage.getInstance();

		ResumableInfo info = storage.get(resumableChunkSize,
				resumableTotalSize, resumableIdentifier, resumableFilename,
				resumableRelativePath);
		if (!info.vaild()) {
			storage.remove(info);
			throw new Exception("Invalid request params.");
		}
		return info;
	}
	
	public static void main(String[] args) {
		if (args.length != 2 && args.length != 3) {
			help();
			return;
		}

		try {
			int port = java.lang.Integer.parseInt(args[0]);
			port(port);
		} catch (NumberFormatException e) {
			help();
			return;
		}
		final String uploadFileLocation = args[1];
		
		final String basic;
		if(args.length == 3){
			basic = Base64.getEncoder().encodeToString(args[2].getBytes());
		}else{
			basic = null;
		}

		get("/*", (req, resp) -> {
			String name = null;
			File f = null;

			if ("/".equals(req.pathInfo()) && req.queryParams().size() > 0) {					
				if(req.queryParams().size() == 1){
					name = req.queryParams().iterator().next();
					outFile(Server.class.getResourceAsStream(staticFileLocation	+ name), name, resp);
					return "";
				}else{
					
					Map<String, String> map = new HashMap<String, String>();
			        for (Entry<String, String[]> key : req.queryMap().toMap().entrySet()) {
			            map.put(key.getKey(), key.getValue()[0]);
			        }
					ResumableInfo info = getResumableInfo(map);
					int resumableChunkNumber = Integer.parseInt(req.queryParams("resumableChunkNumber"));
			        if (info.uploadedChunks.contains(new ResumableInfo.ResumableChunkNumber(resumableChunkNumber))) {
			            return "Uploaded."; //This Chunk has been Uploaded.
			        } else {
			        	halt(HttpServletResponse.SC_NO_CONTENT);
			        }
				}
			} else {
				name = req.pathInfo();
				f = new File(uploadFileLocation + name);
			}
			if (f.isDirectory()) {
				return lsdir(f, req.pathInfo(), resp);
			} else if (f.isFile()) {
				outFile(f, name, resp);
			} else if (!f.exists()) {
				log("File[" + f.getAbsolutePath() + "] not found.");
				halt(HttpServletResponse.SC_NOT_FOUND,"404 Resource not found");
			}

			// resp.redirect("/index.html", 301);
			return "";
		});

		post("/", (request, response) -> {
			boolean isMultipart = ServletFileUpload.isMultipartContent(request.raw());
			if (isMultipart) {
				// Create a new file upload handler
				ServletFileUpload upload = new ServletFileUpload();
				// Parse the request
				RandomAccessFile raf = null;
				Map<String, String> map = new HashMap<String, String>();
				try {
					FileItemIterator iter = upload.getItemIterator(request.raw());
					
					while (iter.hasNext()) {
						FileItemStream item = iter.next();
						String name = item.getFieldName();
						InputStream stream = item.openStream();
						if (item.isFormField()) {
							map.put(name, IOUtils.toString(stream));
						} else {
							int resumableChunkNumber = Integer.parseInt(map.get("resumableChunkNumber"));
							long position = (resumableChunkNumber - 1) * Long.parseLong(map.get("resumableChunkSize"));
							int size = Integer.parseInt(map.get("resumableCurrentChunkSize"));
							int readed = 0;

							// Process the input stream
							raf = new RandomAccessFile(uploadFileLocation + map.get("resumableFilename"), "rw");
							// Seek to position
							raf.seek(position);
							// Save to file
							byte[] bytes = new byte[1024 * 8];
							for (int r; (r = stream.read(bytes)) != -1;) {
								raf.write(bytes, 0, r);
								readed += r;
							}
							IOUtils.closeQuietly(raf);

							if (readed != size) {
								halt(206, "Chunk broken");
							}

							ResumableInfo info = getResumableInfo(map);
							info.uploadedChunks.add(new ResumableInfo.ResumableChunkNumber(resumableChunkNumber));
							
							log("File["	+ map.get("resumableFilename") + "] chunk "	+ map.get("resumableChunkNumber")
									+ " saved, total " + map.get("resumableTotalChunks"));
							if (info.checkIfUploadFinished()) { //Check if all chunks uploaded, and change filename
					            ResumableInfoStorage.getInstance().remove(info);
					            log("File["	+ map.get("resumableFilename") + "] All finished.");
					        }
						}
					}
				} catch (java.io.EOFException e) {
					log("EOFException.");
					halt(HttpServletResponse.SC_BAD_REQUEST);
				} finally {
					IOUtils.closeQuietly(raf);
				}
			}
			return "Chunk finished";
		});
		
		before((request, response) -> {
			if(basic == null) return;
			
			String authorization = request.headers("Authorization");
			if(authorization != null){
				authorization = authorization.replaceFirst("^Basic ", "");
				if(basic.equals(authorization)){
					return;
				}
			}
		    
			response.header("WWW-Authenticate", "Basic realm=\"Login Required\"");
		    halt(HttpServletResponse.SC_UNAUTHORIZED, "You are not welcome here");
		});


//		exception(RuntimeException.class, (e, request, response) -> {			
//		});
	}
}
