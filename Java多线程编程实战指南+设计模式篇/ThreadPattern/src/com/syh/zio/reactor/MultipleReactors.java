package com.syh.zio.reactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created with IntelliJ IDEA.
 * User: Administrator
 * Date: 19-4-4
 * Time: 下午12:12
 * To change this template use File | Settings | File Templates.
 */
public class MultipleReactors {
    final Selector selector;
    final ServerSocketChannel serverSocket;

    public MultipleReactors(int port) throws IOException {
        selector = Selector.open();
        serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(port));
        serverSocket.configureBlocking(false);
        SelectionKey sk = serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        sk.attach(new MultiWorkThreadAcceptor());
    }

    /**
     * 多work 连接事件Acceptor,处理连接事件
     */
    class MultiWorkThreadAcceptor implements Runnable {

        // cpu线程数相同多work线程
        int workCount =Runtime.getRuntime().availableProcessors();
        SubReactor[] workThreadHandlers = new SubReactor[workCount];
        volatile int nextHandler = 0;

        public MultiWorkThreadAcceptor() {
            this.init();
        }

        public void init() {
            nextHandler = 0;
            for (int i = 0; i < workThreadHandlers.length; i++) {
                try {
                    workThreadHandlers[i] = new SubReactor();
                } catch (Exception e) {
                }
            }
        }

        @Override
        public void run() {
            try {
                SocketChannel c = serverSocket.accept();
                if (c != null) {// 注册读写
                    synchronized (c) {
                        // 顺序获取SubReactor，然后注册channel
                        SubReactor work = workThreadHandlers[nextHandler];
                        work.registerChannel(c);
                        nextHandler++;
                        if (nextHandler >= workThreadHandlers.length) {
                            nextHandler = 0;
                        }
                    }
                }
            } catch (Exception e) {
            }
        }

        /**
         * 多work线程处理读写业务逻辑
         */
        class SubReactor implements Runnable {
            final Selector mySelector;

            //多线程处理业务逻辑
            int workCount =Runtime.getRuntime().availableProcessors();
            ExecutorService executorService = Executors.newFixedThreadPool(workCount);

            public SubReactor() throws Exception {
                // 每个SubReactor 一个selector
                this.mySelector = SelectorProvider.provider().openSelector();
            }

            /**
             * 注册chanel
             *
             * @param sc
             * @throws Exception
             */
            public void registerChannel(SocketChannel sc) throws Exception {
                sc.register(mySelector, SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
            }

            @Override
            public void run() {
                while (true) {
                    try {
                        //每个SubReactor 自己做事件分派处理读写事件
                        selector.select();
                        Set<SelectionKey> keys = selector.selectedKeys();
                        Iterator<SelectionKey> iterator = keys.iterator();
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();
                            if (key.isReadable()) {
                                read();
                            } else if (key.isWritable()) {
                                write();
                            }
                        }

                    } catch (Exception e) {
                    }
                }
            }

            private void read() {
                //任务异步处理
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        process();
                    }
                });
            }

            private void write() {
                //任务异步处理
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        process();
                    }
                });
            }

            /**
             * task 业务处理
             */
            public void process() {
                //do IO ,task,queue something
            }
        }

    }
}
