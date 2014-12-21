package com.chenhm.html5upload;

import static spark.Spark.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;

import spark.Response;

public class Server {
	private static final String staticFileLocation = "/public/";

	private static void help() {
		System.out
				.println("Usage: java -jar html5upload.jar LISTEN_PORT UPLOAD_PATH");
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

	private static void lsdir(File dir, String path, Response resp)
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
		String htmlTemplate = IOUtils.toString(Server.class
				.getResourceAsStream(staticFileLocation + "index.html"));

		Map<String, String> map = new HashMap<String, String>();
		map.put("fileList", sb.toString());
		halt(format(htmlTemplate, map));
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

	public static void main(String[] args) {
		if (args.length != 2) {
			help();
			return;
		}

		try {
			int port = java.lang.Integer.parseInt(args[0]);
			setPort(port);
		} catch (NumberFormatException e) {
			help();
			return;
		}
		final String uploadFileLocation = args[1];

		get("/*", (req, resp) -> {
			String name;
			File f;
			if ("/".equals(req.pathInfo()) && req.queryParams().size() > 0) {
				name = req.queryParams().iterator().next();
				f = new File(Server.class
						.getResource(staticFileLocation + name).getFile());
			} else {
				name = req.pathInfo();
				f = new File(uploadFileLocation + name);
			}

			// System.out.println(f);
				try {
					if (f.isDirectory()) {
						lsdir(f, req.pathInfo(), resp);
					} else if (f.isFile()) {
						Pattern p = Pattern.compile("\\.([^\\s\\.]+)$");
						Matcher m = p.matcher(name);
						if (m.find()) {
							String ext = m.group(1);
							String mime = getMIME(ext);
							if (mime != null)
								resp.type(mime);
						}
						IOUtils.copy(new java.io.FileInputStream(f), resp.raw()
								.getOutputStream());
					} else if (!f.exists()) {
						resp.status(404);
						resp.body("404 Resource not found");
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}

				// resp.redirect("/index.html", 301);
				return "";
			});

		post("/",
				(request, response) -> {
					boolean isMultipart = ServletFileUpload
							.isMultipartContent(request.raw());
					if (isMultipart) {
						// Create a new file upload handler
						ServletFileUpload upload = new ServletFileUpload();

						// Parse the request
						try {
							FileItemIterator iter = upload
									.getItemIterator(request.raw());
							Map<String, String> map = new HashMap<String, String>();

							while (iter.hasNext()) {
								FileItemStream item = iter.next();
								String name = item.getFieldName();
								InputStream stream = item.openStream();
								if (item.isFormField()) {
									map.put(name, IOUtils.toString(stream));
								} else {
									// Process the input stream
									RandomAccessFile raf = new RandomAccessFile(
											uploadFileLocation
													+ map.get("resumableFilename"),
											"rw");

									// Seek to position
									raf.seek((Integer.parseInt(map
											.get("resumableChunkNumber")) - 1)
											* Long.parseLong(map
													.get("resumableChunkSize")));

									// Save to file
									long readed = 0;
									byte[] bytes = new byte[1024 * 100];
									for (int r; (r = stream.read(bytes)) != -1;) {
										raf.write(bytes, 0, r);
										readed += r;
									}
									raf.close();

									if (readed != Integer.parseInt(map
											.get("resumableCurrentChunkSize"))) {
										halt(206, "Chunk broken");
									}
									System.out.println("File["
											+ map.get("resumableFilename") + "] chunk "
											+ map.get("resumableChunkNumber") + " saved, total "
											+ map.get("resumableTotalChunks"));
								}
							}
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}
					return "Chunk finished";
				});

		exception(RuntimeException.class, (e, request, response) -> {
			if (e.getCause() instanceof spark.HaltException) {
				spark.HaltException halt = (spark.HaltException) e.getCause();
				response.status(halt.getStatusCode());
				response.body(halt.getBody());
			} else {
				response.status(404);
				response.body("404 Resource not found");
				e.printStackTrace();
			}
		});
	}
}
