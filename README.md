html5upload
===========
通过http协议多线程上传文件的小工具，支持大文件传输。服务端需要Java 1.8，浏览器需要支持Html5 File API.

1.Build
```
mvn clean compile assembly:single
```
2.Usage
```
java -jar html5upload.jar LISTEN_PORT UPLOAD_PATH
```
