package org.jupiter.benchmark.serialization;

import io.netty.buffer.*;
import io.netty.util.internal.PlatformDependent;
import org.jupiter.common.util.Lists;
import org.jupiter.serialization.*;
import org.jupiter.transport.netty.alloc.AdaptiveOutputBufAllocator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Fork(1)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SerializationBenchmark {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SerializationBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }

    private static final Serializer javaSerializer = SerializerFactory.getSerializer(SerializerType.JAVA.value());
    private static final Serializer hessianSerializer = SerializerFactory.getSerializer(SerializerType.HESSIAN.value());
    private static final Serializer protoStuffSerializer = SerializerFactory.getSerializer(SerializerType.PROTO_STUFF.value());
    private static final Serializer kryoSerializer = SerializerFactory.getSerializer(SerializerType.KRYO.value());

    private static final AdaptiveOutputBufAllocator.Handle allocHandle = AdaptiveOutputBufAllocator.DEFAULT.newHandle();
    private static final ByteBufAllocator allocator = new PooledByteBufAllocator(PlatformDependent.directBufferPreferred());

    @Benchmark
    public void javaBytesArray() {
        byte[] bytes = javaSerializer.writeObject(createUser());
        javaSerializer.readObject(bytes, User.class);
    }

    @Benchmark
    public void javaByteBuffer() {
        OutputBuf  outputBuf = javaSerializer.writeObject(new NettyOutputBuf(allocHandle, allocator), createUser());
        InputBuf inputBuf = new NettyInputBuf((ByteBuf) outputBuf.attach());
        javaSerializer.readObject(inputBuf, User.class);
    }

    @Benchmark
    public void hessianBytesArray() {
        byte[] bytes = hessianSerializer.writeObject(createUser());
        hessianSerializer.readObject(bytes, User.class);
    }

    @Benchmark
    public void hessianByteBuffer() {
        OutputBuf outputBuf = hessianSerializer.writeObject(new NettyOutputBuf(allocHandle, allocator), createUser());
        InputBuf inputBuf = new NettyInputBuf((ByteBuf) outputBuf.attach());
        hessianSerializer.readObject(inputBuf, User.class);
    }

    @Benchmark
    public void protoStuffBytesArray() {
        byte[] bytes = protoStuffSerializer.writeObject(createUser());
        protoStuffSerializer.readObject(bytes, User.class);
    }

    @Benchmark
    public void protoStuffByteBuffer() {
        OutputBuf outputBuf = protoStuffSerializer.writeObject(new NettyOutputBuf(allocHandle, allocator), createUser());
        InputBuf inputBuf = new NettyInputBuf((ByteBuf) outputBuf.attach());
        protoStuffSerializer.readObject(inputBuf, User.class);
    }

    @Benchmark
    public void kryoBytesArray() {
        byte[] bytes = kryoSerializer.writeObject(createUser());
        kryoSerializer.readObject(bytes, User.class);
    }

    @Benchmark
    public void kryoByteBuffer() {
        OutputBuf outputBuf = kryoSerializer.writeObject(new NettyOutputBuf(allocHandle, allocator), createUser());
        InputBuf inputBuf = new NettyInputBuf((ByteBuf) outputBuf.attach());
        kryoSerializer.readObject(inputBuf, User.class);
    }

    static final class NettyInputBuf implements InputBuf {

        private final ByteBuf byteBuf;

        NettyInputBuf(ByteBuf byteBuf) {
            this.byteBuf = byteBuf;
        }

        @Override
        public InputStream inputStream() {
            return new ByteBufInputStream(byteBuf); // should not be called more than once
        }

        @Override
        public ByteBuffer nioByteBuffer() {
            return byteBuf.nioBuffer(); // should not be called more than once
        }

        @Override
        public int size() {
            return byteBuf.readableBytes();
        }

        @Override
        public boolean release() {
            return byteBuf.release();
        }
    }

    static final class NettyOutputBuf implements OutputBuf {

        private final AdaptiveOutputBufAllocator.Handle allocHandle;
        private final ByteBuf byteBuf;
        private ByteBuffer nioByteBuffer;

        public NettyOutputBuf(AdaptiveOutputBufAllocator.Handle allocHandle, ByteBufAllocator alloc) {
            this.allocHandle = allocHandle;
            byteBuf = allocHandle.allocate(alloc);
        }

        @Override
        public OutputStream outputStream() {
            return new ByteBufOutputStream(byteBuf); // should not be called more than once
        }

        @Override
        public ByteBuffer nioByteBuffer(int minWritableBytes) {
            if (minWritableBytes < 0) {
                minWritableBytes = byteBuf.writableBytes();
            }

            if (nioByteBuffer == null) {
                nioByteBuffer = newNioByteBuffer(byteBuf, minWritableBytes);
            }

            if (nioByteBuffer.remaining() >= minWritableBytes) {
                return nioByteBuffer;
            }

            int position = nioByteBuffer.position();

            nioByteBuffer = newNioByteBuffer(byteBuf, position + minWritableBytes);

            nioByteBuffer.position(position);

            return nioByteBuffer;
        }

        @Override
        public int size() {
            if (nioByteBuffer == null) {
                return byteBuf.readableBytes();
            }
            return Math.max(byteBuf.readableBytes(), nioByteBuffer.position());
        }

        @Override
        public Object attach() {
            int actualWriteBytes = byteBuf.writerIndex();
            if (nioByteBuffer != null) {
                actualWriteBytes += nioByteBuffer.position();
            }

            allocHandle.record(actualWriteBytes);

            return byteBuf.writerIndex(actualWriteBytes);
        }

        private static ByteBuffer newNioByteBuffer(ByteBuf byteBuf, int writableBytes) {
            return byteBuf
                    .ensureWritable(writableBytes)
                    .nioBuffer(byteBuf.writerIndex(), byteBuf.writableBytes());
        }
    }

    static User createUser() {
        User user = new User();
        user.setId(1);
        user.setName("block");
        user.setSex(0);
        user.setBirthday(new Date());
        user.setEmail("xxx@alibaba-inc.con");
        user.setMobile("18325038521");
        user.setAddress("浙江省 杭州市 文一西路969号");
        user.setPermissions(Lists.newArrayList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));
        user.setStatus(1);
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());
        return user;
    }

    static class User implements Serializable {

        private long id;
        private String name;
        private int sex;
        private Date birthday;
        private String email;
        private String mobile;
        private String address;
        private List<Integer> permissions;
        private int status;
        private Date createTime;
        private Date updateTime;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getSex() {
            return sex;
        }

        public void setSex(int sex) {
            this.sex = sex;
        }

        public Date getBirthday() {
            return birthday;
        }

        public void setBirthday(Date birthday) {
            this.birthday = birthday;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getMobile() {
            return mobile;
        }

        public void setMobile(String mobile) {
            this.mobile = mobile;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public List<Integer> getPermissions() {
            return permissions;
        }

        public void setPermissions(List<Integer> permissions) {
            this.permissions = permissions;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public Date getCreateTime() {
            return createTime;
        }

        public void setCreateTime(Date createTime) {
            this.createTime = createTime;
        }

        public Date getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(Date updateTime) {
            this.updateTime = updateTime;
        }

        @Override
        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", sex=" + sex +
                    ", birthday=" + birthday +
                    ", email='" + email + '\'' +
                    ", mobile='" + mobile + '\'' +
                    ", address='" + address + '\'' +
                    ", permissions=" + permissions +
                    ", status=" + status +
                    ", createTime=" + createTime +
                    ", updateTime=" + updateTime +
                    '}';
        }
    }
}
