package cc.spray.can.spdy
package pipeline

import annotation.tailrec
import java.nio.ByteBuffer

import java.util.zip.Inflater

import cc.spray.io._

import cc.spray.can.parsing.{IntermediateState, ParsingState}

object SpdyParsing {
  def apply(): EventPipelineStage = new EventPipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): EPL = {
      val inflater = new Inflater()
      def startParser = new parsing.FrameHeaderReader(inflater)

      var currentParsingState: ParsingState = startParser

      @tailrec
      def parse(buffer: ByteBuffer) {
        currentParsingState match {
          case x: IntermediateState =>
            if (buffer.remaining > 0) {
              currentParsingState = x.read(buffer)
              parse(buffer)
            } // else wait for more input

          case f: Frame =>
            //println("Got frame "+f.getClass)
            eventPL(SpdyFrameReceived(f))

            currentParsingState = startParser
            parse(buffer)

          case x: FrameParsingError =>
            println("Got error "+x)
            commandPL(IOServer.Close(ProtocolError("Got error "+x)))
            currentParsingState = startParser
        }
      }

      {
        case x: IOServer.Received =>
          parse(x.buffer)
          //println("After parsing at "+currentParsingState)
        case x => eventPL(x)
      }
    }
  }

  // EVENTS
  case class SpdyFrameReceived(frame: Frame) extends Event
}

