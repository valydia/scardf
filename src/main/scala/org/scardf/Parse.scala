package org.scardf

import java.io.Reader
//import scala.collection.mutable.ArrayStack

/**
 * Attempting to follow the EBNF at http://www.w3.org/TR/rdf-testcases/#ntriples
 * Not fully parsing language tags yet.
 */

object Parse {
  val space = 32
  val tab = 9
  val hexDigits4 = Array(4096, 256, 16, 1)
  val hexDigits6 = Array(1048576, 65536, 4096, 256, 16, 1)
  def fromHex(c:Int) = c match {
    case l if Character.isLetter(l) => l - 55
    case d if Character.isDigit(d) => d - 48
  }

  val sb = new StringBuilder(256)

  def apply(reader: Reader) = {
    val graph = new MutableSetGraph()
    //val triples = new ArrayStack[RdfTriple]
    var line = 1
    var char = reader.read()

    def optionalWhitespace {
      while ((char == space || char == tab) && char != -1) char = reader.read
    }
    def whitespace {
      if (char != space && char != tab)
        error("Whitespace expected on line " + line + ", '" + char.toChar + "' found.")
      else
        optionalWhitespace
    }

    def eoln {
      if (char == 13) {
        char = reader.read
        if (char == 10)
          char = reader.read
      } else if (char == 10) {
        char = reader.read
      } else {
        error("Line-end expected on line " + line + ", '" + char.toChar + "' found.")
      }
    }

    def takeUntil(c: Int) = {
      sb.clear()
      while (char != c && char != -1) {
        sb.append(char.toChar)
        char = reader.read
      }
      char = reader.read
      sb.toString
    }

    def takeWhile(test: Int => Boolean) = {
      sb.clear()
      while (test(char) && char != -1) {
        sb.append(char.toChar)
        char = reader.read
      }
      sb.toString
    }

    def lowercaseChars = takeWhile(Character.isLowerCase)
    def alphaNumericChars = takeWhile(Character.isLetterOrDigit)

    def require(c: Char) {
      if (char != c)
        error("'" + c + "' expected on line " + line + ", '" + char.toChar + "' found.")
      else
        char = reader.read
    }

    def uriref = UriRef({
      require('<')
      takeUntil('>')
    })

    def blank = Blank({
      require('_')
      require(':')
      alphaNumericChars
    })

    def subjectNode = char match {
      case '<' => uriref
      case '_' => blank
      case other => error("'<' or '_' expected on line " + line + ", '" + other.toChar + "' found.")
    }

    def literal = {
      require('"')
      val string = {
        sb.clear()
        while (char != '"' && char != -1) {
          sb.append((if (char == '\\') { // Escaped char
            char = reader.read
            char match {
              case 'u' => hexDigits4.foldLeft(0)((sum, i)=> {char = reader.read; sum + i * fromHex(char)})
              case 'U' => hexDigits4.foldLeft(0)((sum, i)=> {char = reader.read; sum + i * fromHex(char)})
                // Integer.valueOf("ABCD",16).intValue.toChar
              case 't' => '\t'
              case 'n' => '\n'
              case 'r' => '\r'
              case other => other
            }
          } else { // Regular character
            char
          }) toChar)
          //sb.append(char.toChar)
          char = reader.read
        }
        char = reader.read
      sb.toString
      }
      char match {
        case '^' => {
          char = reader.read
          require('^')
          TypedLiteral(string, uriref)
        }
        case '@' => {
          char = reader.read
          val language = lowercaseChars
          val region = if (char == '-') {
            char = reader.read
            "-" + alphaNumericChars
          } else {
            ""
          }
          PlainLiteral(string, Some(LangTag(language + region))) // TODO: Handle the possibility of ('-' [a-z0-9]+ )*
        }
        case other => PlainLiteral(string)
      }
    }

    def node = char match {
      case '"' => literal
      case '<' => uriref
      case '_' => blank
      case other => error("'\"', '<', or '_' expected on line " + line + ", '" + other.toChar + "' found.")
    }

    // The actual parsing loop
    while (char != -1) {

      optionalWhitespace
      val subject = subjectNode
      whitespace
      val predicate = uriref
      whitespace
      val obj = node
      optionalWhitespace
      require('.')
      optionalWhitespace
      eoln

      line = line + 1
      print(subject)
      print(predicate)
      println(obj)
      //graph + RdfTriple(subject, predicate, obj)
      //triples += RdfTriple(subject, predicate, obj)
    }

    reader.close()
    //Graph() ++ triples
    graph
  }
}