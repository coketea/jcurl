package com.coketea.jcurl;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Johnson Wong on 2017/11/8.
 */
public class HttpClient {

    private Socket socket;

    private String host;

    private int port = 80;

    private String method = "GET";

    private String requestUri = "/";

    private Map<String, String> requestHeaders = new HashMap<String, String>();

    private String requestBody = "";

    private String responseCode;

    private String responseStatus;

    private Map<String, String> responseHeaders = new HashMap<String, String>();

    public HttpClient() {
        this.socket = new Socket();
    }

    public HttpClient(String host) {
        this(host, 80);
    }

    public HttpClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.socket = new Socket();
    }

    public void addRequestHeader(String key, String value) {
        this.requestHeaders.put(key, value);
    }

    public String getResponseHeader(String key) {
        return this.responseHeaders.get(key);
    }

    /**
     * 创建请求头报文
     * @return 拼接好的请求头报文字符串
     * @throws UnsupportedEncodingException
     */
    private String createRequestHeader() throws UnsupportedEncodingException {
        StringBuilder headerStr = new StringBuilder();
        headerStr.append(this.method).append(" ").append(this.requestUri).append(" HTTP/1.1\r\n");
        headerStr.append("Host: ").append(this.host).append("\r\n");
        headerStr.append("Content-Length: ").append(this.requestBody.getBytes("UTF-8").length).append("\r\n");
        for (Map.Entry<String, String> header : this.requestHeaders.entrySet()) {
            headerStr.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        headerStr.append("\r\n");
        return headerStr.toString();
    }

    /**
     * 读取服务器端一行数据
     * @param is 从socket获取的inputStream
     * @return 读取到的一行字节数组，不包含回车换行符
     * @throws IOException
     */
    private byte[] readLine(InputStream is) throws IOException {
        ByteArrayOutputStream baos = null;
        int b1, b2;
        try {
            baos = new ByteArrayOutputStream();
            b1 = is.read();
            while (true) {
                if (b1 == -1) {
                    break;
                }
                b2 = is.read();
                if (b1 == '\r' && b2 == '\n') {
                    break;
                }
                baos.write(b1);
                b1 = b2;
            }
            return baos.toByteArray();
        } finally {
            if (baos != null) {
                try {
                    baos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 读取服务器端一行数据，并转为字符创返回
     * @param is 从socket获取的inputStream
     * @return 读取到的一行字节数组，不包含回车换行符
     * @throws IOException
     */
    private String readStringLine(InputStream is) throws IOException {
        return new String(this.readLine(is), "UTF-8");
    }

    /**
     * 解析服务器端返回码与返回状态
     * @param is 从socket获取的inputStream
     * @throws IOException
     */
    private void parseResponseStatus(InputStream is) throws IOException {
        String line = new String(this.readLine(is), "UTF-8");
        int idx = line.indexOf(" ");
        if (idx != -1) {
            line = line.substring(idx + 1);
            idx = line.indexOf(" ");
            if (idx == -1) {
                this.responseCode = line;
            } else {
                this.responseCode = line.substring(0, idx);
                this.responseStatus = line.substring(idx + 1);
            }
        }
    }

    /**
     * 解析服务器端响应报文头
     * @param is 从socket获取的inputStream
     * @return 解析出来的报文头键值对
     * @throws IOException
     */
    private void parseResponseHeader(InputStream is) throws IOException {
        String line = null;
        while (true) {
            line = new String(this.readLine(is), "UTF-8");
            if (line.length() == 0) {
                break;
            }

            int idx = line.indexOf(":");
            if (idx == -1) {
                responseHeaders.put(line, "");
            } else {
                responseHeaders.put(line.substring(0, idx), line.substring(idx + 1).trim());
            }
        }
    }

    /**
     * 解析Content-Length指定内容长度的报文体
     * @param is 从socket获取的inputStream
     * @return
     * @throws IOException
     */
    private byte[] parseResponseBody(InputStream is) throws IOException {
        long length = Long.parseLong(this.responseHeaders.get(Constance.HTTP_RESPONSE_HEADER_KEY_CONTENT_LENGTH));
        ByteArrayOutputStream baos = null;
        try {
            baos = new ByteArrayOutputStream();
            int readCount = 0;
            long totalReadCount = 0;
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            //totalReadCount < length这个条件必须写在前面，否则在一次读取就已经把数据取完的场景下会出现死循环
            while (totalReadCount < length && (readCount = is.read(buffer, 0, bufferSize)) != -1) {
                totalReadCount += readCount;
                baos.write(buffer, 0, readCount);
            }
            return baos.toByteArray();
        } finally {
            if (baos != null) {
                try {
                    baos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 解析Chunked编码的报文体
     * @param is 从socket获取的inputStream
     * @return
     * @throws IOException
     */
    private byte[] parseResponseBodyChunked(InputStream is) throws IOException {
        ByteArrayOutputStream baos = null;
        try {
            baos = new ByteArrayOutputStream();
            int length = Integer.parseInt(this.readStringLine(is), 16);
            while (length != 0) {
                int totalReadCount = 0, readCount = 0;
                byte[] buffer = new byte[length];
                while (totalReadCount < length) {
                    readCount = is.read(buffer, totalReadCount, length - totalReadCount);
                    totalReadCount += readCount;
                }
                baos.write(buffer);
                //跳过结尾回车换行符
                this.readLine(is);
                length = Integer.parseInt(this.readStringLine(is), 16);
            }
            return baos.toByteArray();
        } finally {
            if (baos != null) {
                try {
                    baos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 发送http请求并获取返回的二进制数据
     * @return 获取到的二进制数据数组
     * @throws Exception
     */
    public byte[] fetch() throws Exception {
        BufferedWriter bufferedWriter = null;
        InputStream inputStream = null;

        try {
            SocketAddress dest = new InetSocketAddress(host, port);
            socket.connect(dest);
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            bufferedWriter.write(this.createRequestHeader());
            bufferedWriter.write(this.requestBody);
            bufferedWriter.write("\r\n");
            bufferedWriter.flush();

            inputStream = new BufferedInputStream(socket.getInputStream());
            this.parseResponseStatus(inputStream);
            this.parseResponseHeader(inputStream);
            if (this.responseHeaders.get(Constance.HTTP_RESPONSE_HEADER_KEY_TRANSFER_ENCODING) != null
                    && this.responseHeaders.get(Constance.HTTP_RESPONSE_HEADER_KEY_TRANSFER_ENCODING)
                    .equals(Constance.HTTP_RESPONSE_HEADER_VALUE_TRANSFER_ENCODING_CHUNKED)) {
                return this.parseResponseBodyChunked(inputStream);
            } else {
                return this.parseResponseBody(inputStream);
            }
        } finally {
            if (bufferedWriter != null) {
                try {
                    bufferedWriter.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 发送http请求并获取返回的二进制数据
     * @param requestUri 请求的uri地址
     * @return 获取到的二进制数据数组
     * @throws Exception
     */
    public byte[] fetch(String requestUri) throws Exception {
        this.requestUri = requestUri;
        return fetch();
    }

    /**
     * 发送http请求并获取返回的字符串数据
     * @return 获取到的二进制数据数组
     * @throws Exception
     */
    public String fetchString() throws Exception {
        return new String(this.fetch(), "UTF-8");
    }

    /**
     * 发送http请求并获取返回的字符串数据
     * @param requestUri 请求的uri地址
     * @return 获取到的二进制数据数组
     * @throws Exception
     */
    public String fetchString(String requestUri) throws Exception {
        return new String(this.fetch(requestUri), "UTF-8");
    }

    /**
     * 关闭HttpClient并释放资源
     */
    public void close() {
        if (this.socket != null) {
            try {
                this.socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(Map<String, String> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(String responseStatus) {
        this.responseStatus = responseStatus;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Map<String, String> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }
}
