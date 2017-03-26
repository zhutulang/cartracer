package com.cartracer.my;

import io.netty.channel.ChannelHandlerContext;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * AudienceListner
 */
public class AudienceListener implements Runnable{

	private static ExecutorService exe = Executors.newFixedThreadPool(10);

	public static ConcurrentHashMap<ChannelHandlerContext, Long> audiences = new ConcurrentHashMap<>();

	public static ConcurrentHashMap<ChannelHandlerContext, Future> sendFutures = new ConcurrentHashMap<>();

	private static final Logger logger = Logger.getLogger(LocationMsgSender.class.getName());

	public static void addAudience(ChannelHandlerContext ctx){
		logger.info("新入channel："+ctx.hashCode());
		audiences.putIfAbsent(ctx, System.currentTimeMillis());
		Future f = exe.submit(new LocationMsgSender(ctx));
		sendFutures.putIfAbsent(ctx, f);
		logger.info("audience大小："+audiences.size());
	}

	public static void removeAudience(ChannelHandlerContext ctx){
		logger.info("移除channel："+ctx.hashCode());
		audiences.remove(ctx);
		// 终止该channel LocationMsgSender 线程
		Future f = sendFutures.get(ctx);
		if(f != null){
			f.cancel(true);
		}
		sendFutures.remove(ctx);
	}

	public static void updateAudience(ChannelHandlerContext ctx){
		logger.info("channel 接收到心跳包，更新时间戳");
		audiences.put(ctx, System.currentTimeMillis());
		logger.info("audience大小："+audiences.size());
	}

	@Override
	public void run() {
		try {
			while(true){
				Thread.sleep(10000);
				Iterator<Map.Entry<ChannelHandlerContext, Long>> itr = audiences.entrySet().iterator();
				long currentTime = System.currentTimeMillis();
				while(itr.hasNext()){
					Map.Entry<ChannelHandlerContext, Long> entry = itr.next();
					if(currentTime - entry.getValue() >= 30000){
						// 表明该channel已经断开连接
						removeAudience(entry.getKey());
					}
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}