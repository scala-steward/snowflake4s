/**
 * Copyright (c) 2021 qwbarch
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package snowflake4s

import cats.effect.kernel.Async
import scala.util.hashing.MurmurHash3
import org.typelevel.log4cats.Logger
import cats.syntax.all.given
import cats.effect.kernel.Ref
import cats.effect.std.Semaphore

final class IdWorkerBuilder[F[_]: Async: Logger](
      workerId: Long,
      dataCenterId: Long,
      epoch: Long,
      sequence: Long,
):

   import IdWorker.*

   private def copy(
         workerId: Long = this.workerId,
         dataCenterId: Long = this.dataCenterId,
         epoch: Long = this.epoch,
         sequence: Long = this.sequence,
   ): IdWorkerBuilder[F] = IdWorkerBuilder(workerId, dataCenterId, epoch, sequence)

   override def hashCode: Int =
      var hash = IdWorkerBuilder.hashSeed
      hash = MurmurHash3.mix(hash, workerId.##)
      hash = MurmurHash3.mix(hash, dataCenterId.##)
      hash = MurmurHash3.mix(hash, epoch.##)
      hash = MurmurHash3.mixLast(hash, sequence.##)
      hash

   override def toString: String = show"IdWorkerBuilder($workerId, $dataCenterId, $epoch, $sequence)"

   /**
    * Sets the worker's id.
    */
   def withWorkerId(workerId: Long): IdWorkerBuilder[F] = copy(workerId = workerId)

   /**
    * Sets the worker's data center id.
    */
   def withDataCenterId(dataCenterId: Long): IdWorkerBuilder[F] = copy(dataCenterId = dataCenterId)

   /**
    * Sets the epoch used for generating ids.
    */
   def withEpoch(epoch: Long): IdWorkerBuilder[F] = copy(epoch = epoch)

   /**
    * Sets the sequence id.
    */
   def withSequence(sequence: Long): IdWorkerBuilder[F] = copy(sequence = sequence)

   /**
    * Creates a new id worker using the builder's arguments.
    */
   def build: F[IdWorker[F]] =
      for
         _ <- Async[F].unit
            .ensure(
               new IllegalArgumentException(show"Worker id can't be greater than $MaxWorkerId or less than 0."),
            )(_ => workerId <= MaxWorkerId && workerId >= 0)
            .ensure(
               new IllegalArgumentException(show"Data center id can't be greater than $MaxDataCenterId or less than 0."),
            )(_ => dataCenterId <= MaxDataCenterId && dataCenterId >= 0)
         _ <- Logger[F].info(
            show"Worker starting. Timestamp left shift $TimeStampLeftShift, " +
               show"data center id bits $DataCenterIdBits, worker id bits $WorkerIdBits, " +
               show"sequence bits $SequenceBits, worker id $workerId.",
         )
         lastTimeStamp <- Ref[F].of(-1L)
         sequence <- Ref[F].of(sequence)
         semaphore <- Semaphore[F](1)
      yield IdWorker(lastTimeStamp, sequence, epoch, dataCenterId, workerId, semaphore)

object IdWorkerBuilder:
   private val hashSeed = MurmurHash3.stringHash("IdWorkerBuilder")

   def default[F[_]: Async: Logger]: IdWorkerBuilder[F] = IdWorkerBuilder(
      workerId = 0,
      dataCenterId = 0,
      sequence = 0,
      epoch = IdWorker.TwitterEpoch,
   )
