package zpp;

import org.apache.tomcat.util.buf.ByteChunk;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static java.lang.Thread.sleep;

/**
 * @author zpp
 * @version 1.0.0
 * @className OutputBuffer
 * @description TODO
 * @createTime 2024/7/15 10:22 - Monday
 */
public class OutputBuffer implements ByteChunk.ByteOutputChannel {

    private ByteChunk byteChunk;
    private FileOutputStream fileOutputStream;

    public OutputBuffer() {
        this.byteChunk = new ByteChunk();
        this.byteChunk.setByteOutputChannel(this);
        // 初始大小是3，最大长度是7
        this.byteChunk.allocate(3, 7);
        try {
            this.fileOutputStream = new FileOutputStream("f:\\test\\hello.txt");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void realWriteBytes(byte[] buf, int off, int len) throws IOException {
        fileOutputStream.write(buf, off, len);
    }

    public void flush() throws IOException {
        this.byteChunk.flushBuffer();
    }

    @Override
    public void realWriteBytes(ByteBuffer from) throws IOException {
        fileOutputStream.write(from.array());
    }

    public int doWrite(byte[] bytes) throws IOException {
        for (int i = 0; i < bytes.length; i++) {
            this.byteChunk.append(bytes[ i ]);
        }
        return bytes.length;
    }

    public static void main(String[] args) {
        OutputBuffer outputBuffer = new OutputBuffer();
        byte[] bytes = {1, 2, 3, 4, 5, 6, 7, 8};

        try {
            outputBuffer.doWrite(bytes);
            System.out.println("写入缓冲区成功---开始睡眠20s");
            try {
                sleep(20 * 1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            System.out.println("开始刷新写入本地文件！！！");
            outputBuffer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
