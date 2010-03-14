package org.infinispan.server.hotrod

import org.infinispan.manager.{DefaultCacheManager, CacheManager}
import java.util.concurrent.TimeUnit
import org.infinispan.context.Flag
import org.infinispan.{AdvancedCache, Cache => InfinispanCache}

/**
 * // TODO: Document this
 * @author Galder Zamarreño
 * @since 4.0
 */

class CallerCache(val manager: CacheManager) extends Cache {

   import CallerCache._

   override def put(c: StorageCommand): Response = {
      val cache = getCache(c.cacheName, c.flags)
      (c.lifespan, c.maxIdle) match {
         case (0, 0) => cache.put(c.k, c.v)
         case (x, 0) => cache.put(c.k, c.v, toMillis(c.lifespan), TimeUnit.MILLISECONDS)
         case (x, y) => cache.put(c.k, c.v, toMillis(c.lifespan), TimeUnit.MILLISECONDS, c.maxIdle, TimeUnit.SECONDS)
      }
      new Response(c.id, OpCodes.PutResponse, Status.Success)
   }

   override def get(c: RetrievalCommand): Response = {
      val cache = getCache(c.cacheName, c.flags)
      val value = cache.get(c.k)
      if (value != null)
         new RetrievalResponse(c.id, OpCodes.GetResponse, Status.Success, value.v)
      else
         new RetrievalResponse(c.id, OpCodes.GetResponse, Status.KeyDoesNotExist, null)
   }

   override def putIfAbsent(c: StorageCommand): Response = {
      val cache = getCache(c.cacheName, c.flags)
      val prev =
         (c.lifespan, c.maxIdle) match {
            case (0, 0) => cache.putIfAbsent(c.k, c.v)
            case (x, 0) => cache.putIfAbsent(c.k, c.v, toMillis(c.lifespan), TimeUnit.MILLISECONDS)
            case (x, y) => cache.putIfAbsent(c.k, c.v, toMillis(c.lifespan), TimeUnit.MILLISECONDS, c.maxIdle, TimeUnit.SECONDS)
         }
      if (prev == null)
         new Response(c.id, OpCodes.PutIfAbsentResponse, Status.Success)
      else
         new Response(c.id, OpCodes.PutIfAbsentResponse, Status.OperationNotExecuted)
   }

   private def getCache(cacheName: String, flags: Set[Flag]): InfinispanCache[Key, Value] = {
      val isDefaultCache = cacheName == DefaultCacheManager.DEFAULT_CACHE_NAME
      val isWithFlags = ! flags.isEmpty
      (isDefaultCache, isWithFlags) match {
         case (true, true) => getAdvancedCache.withFlags(flags.toSeq : _*)
         case (true, false) => getAdvancedCache
         case (false, true) => getAdvancedCache(cacheName).withFlags(flags.toSeq : _*)
         case (false, false) => getAdvancedCache(cacheName)
      }
   }

   private def getAdvancedCache(): AdvancedCache[Key, Value] =
      manager.getCache[Key, Value].getAdvancedCache
   
   private def getAdvancedCache(cacheName: String): AdvancedCache[Key, Value] =
      manager.getCache[Key, Value](cacheName).getAdvancedCache


   /**
    * Transforms lifespan pass as seconds into milliseconds
    * following this rule:
    *
    * If lifespan is bigger than number of seconds in 30 days,
    * then it is considered unix time. After converting it to
    * milliseconds, we substract the current time in and the
    * result is returned.
    *
    * Otherwise it's just considered number of seconds from
    * now and it's returned in milliseconds unit.
    */
   private def toMillis(lifespan: Int) = {
      if (lifespan > SecondsInAMonth) TimeUnit.SECONDS.toMillis(lifespan) - System.currentTimeMillis
      else TimeUnit.SECONDS.toMillis(lifespan)
   }
}

object CallerCache extends Logging {
   private val SecondsInAMonth = 60 * 60 * 24 * 30
}