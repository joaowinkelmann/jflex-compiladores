/*
 * Copyright (C) 1998-2018  Gerwin Klein <lsf@jflex.de>
 * SPDX-License-Identifier: BSD-3-Clause
 */

package jflex.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jflex.chars.Interval;
import jflex.core.unicode.CharClasses;
import jflex.core.unicode.IntCharSet;
import jflex.core.unicode.UnicodeProperties;
import jflex.exceptions.CharClassException;
import jflex.exceptions.GeneratorException;
import jflex.l10n.ErrorMessages;
import jflex.logging.Out;
import jflex.option.Options;

/**
 * Stores a regular expression of rules section in a JFlex-specification.
 *
 * <p>This base class has no content other than its type.
 *
 * @author Gerwin Klein
 * @version JFlex 1.9.1
 */
public class RegExp {

  /**
   * The type of the regular expression. This field will be filled with values from class sym.java
   * (generated by cup)
   */
  int type;

  /**
   * Create a new regular expression of the specified type.
   *
   * @param type a value from the cup generated class sym.
   */
  public RegExp(int type) {
    this.type = type;
  }

  /**
   * Returns a String-representation of this regular expression with the specified indentation.
   *
   * @param tab a String that should contain only space characters and that is inserted in front of
   *     standard String-representation pf this object.
   * @return a {@link java.lang.String} object.
   */
  public String print(String tab) {
    return tab + toString();
  }

  @Override
  public String toString() {
    return "type = " + typeName();
  }

  /** String representation of the type of this regular expression. */
  public String typeName() {
    return sym.terminalNames[type];
  }

  /**
   * Find out if this regexp is a char class or equivalent to one.
   *
   * @return true if the regexp is equivalent to a char class.
   */
  public boolean isCharClass() {
    switch (type) {
      case sym.CHAR:
      case sym.CHAR_I:
      case sym.PRIMCLASS:
        return true;

      case sym.BAR:
        RegExp2 binary = (RegExp2) this;
        return binary.r1.isCharClass() && binary.r2.isCharClass();

      default:
        return false;
    }
  }

  /**
   * The approximate number of NFA states this expression will need (only works correctly after
   * macro expansion and without negation)
   *
   * @param macros macro table for expansion
   * @return a int.
   */
  public int size(Macros macros) {
    RegExp1 unary;
    RegExp2 binary;
    RegExp content;

    switch (type) {
      case sym.BAR:
        binary = (RegExp2) this;
        return binary.r1.size(macros) + binary.r2.size(macros) + 2;

      case sym.CONCAT:
        binary = (RegExp2) this;
        return binary.r1.size(macros) + binary.r2.size(macros);

      case sym.STAR:
      case sym.PLUS:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        return content.size(macros) + 2;

      case sym.QUESTION:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        return content.size(macros);

      case sym.BANG:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        return content.size(macros) * content.size(macros);
        // this is only a very rough estimate (worst case 2^n)
        // exact size too complicated (propably requires construction)

      case sym.TILDE:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        return content.size(macros) * content.size(macros) * 3;
        // see sym.BANG

      case sym.STRING:
      case sym.STRING_I:
        unary = (RegExp1) this;
        return ((String) unary.content).length() + 1;

      case sym.CHAR:
      case sym.CHAR_I:
        return 2;

      case sym.CCLASS:
      case sym.CCLASSNOT:
      case sym.CCLASSOP:
      case sym.PRIMCLASS:
        return 2;

      case sym.MACROUSE:
        unary = (RegExp1) this;
        return macros.getDefinition((String) unary.content).size(macros);

      default:
        throw new RegExpException(this);
    }
  }

  /** Reverses a string. */
  static String revString(String s) {
    return new StringBuilder(s).reverse().toString();
  }

  /**
   * Recursively convert tilde (upto) expressions into negation and star.
   *
   * @return new RegExp equivalent to the current one, but without upto expressions.
   */
  public final RegExp resolveTilde() {
    RegExp1 unary;
    RegExp2 binary;
    RegExp content;

    switch (type) {
      case sym.BAR:
        binary = (RegExp2) this;
        return new RegExp2(sym.BAR, binary.r1.resolveTilde(), binary.r2.resolveTilde());

      case sym.CONCAT:
        binary = (RegExp2) this;
        return new RegExp2(sym.CONCAT, binary.r1.resolveTilde(), binary.r2.resolveTilde());

      case sym.STAR:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        return new RegExp1(sym.STAR, content.resolveTilde());

      case sym.PLUS:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        return new RegExp1(sym.PLUS, content.resolveTilde());

      case sym.QUESTION:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        return new RegExp1(sym.QUESTION, content.resolveTilde());

      case sym.BANG:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        return new RegExp1(sym.BANG, content.resolveTilde());

      case sym.TILDE:
        // ~a = !([^]* a [^]*) a
        // uses subexpression sharing
        unary = (RegExp1) this;
        content = ((RegExp) unary.content).resolveTilde();

        RegExp any_star = new RegExp1(sym.STAR, anyChar());
        RegExp neg =
            new RegExp1(
                sym.BANG,
                new RegExp2(sym.CONCAT, any_star, new RegExp2(sym.CONCAT, content, any_star)));

        return new RegExp2(sym.CONCAT, neg, content);

      case sym.STRING:
      case sym.STRING_I:
      case sym.CHAR:
      case sym.CHAR_I:
      case sym.PRIMCLASS:
        unary = (RegExp1) this;
        return new RegExp1(unary.type, unary.content);

      default:
        throw new RegExpException(this);
    }
  }

  /**
   * Returns a regexp that matches any character: {@code [^]}
   *
   * @return the regexp for {@code [^]}
   */
  public static RegExp anyChar() {
    return new RegExp1(sym.PRIMCLASS, IntCharSet.allChars());
  }

  /**
   * Confirms that the parameter is a RegExp1 of type sym.PRIMCLASS.
   *
   * @param r the RegExp to check
   * @throws CharClassException if r is not a RegExp1 or of type sym.PRIMCLASS.
   * @return r cast to RegExp1
   */
  public static RegExp1 checkPrimClass(RegExp r) {
    if (!(r instanceof RegExp1 && r.type == sym.PRIMCLASS))
      throw new CharClassException("Not normalised " + r);
    return (RegExp1) r;
  }

  /**
   * Performs the given set operation on the two {@link IntCharSet} parameters.
   *
   * @param op the operation to perform (as @{link sym} constant)
   * @param l the left operator of the expression
   * @param r the right operator of the expression
   * @param ctxt the regular expression containing the provided operator
   * @return a new {@link IntCharSet}
   * @throws RegExpException for {@code ctxt} if the operator is not supported
   */
  public static IntCharSet performClassOp(int op, IntCharSet l, IntCharSet r, RegExp ctxt) {
    IntCharSet set;
    IntCharSet intersection = l.and(r);

    switch (op) {
      case sym.INTERSECTION:
        return intersection;

      case sym.DIFFERENCE:
        // IntCharSet.sub() assumes its argument is a subset, so subtract intersection
        set = IntCharSet.copyOf(l);
        set.sub(intersection);
        return set;

      case sym.SYMMETRICDIFFERENCE:
        set = IntCharSet.copyOf(l);
        set.add(r);
        set.sub(intersection);
        return set;

      default:
        throw new RegExpException(ctxt);
    }
  }

  /**
   * Normalise the regular expression to eliminate macro use (expand them).
   *
   * @return a regexp that contains no {@link sym#MACROUSE}.
   */
  @SuppressWarnings("unchecked")
  public final RegExp normaliseMacros(Macros m) {
    RegExp1 unary;
    RegExp2 binary;
    RegExp content;

    switch (type) {
      case sym.BAR:
      case sym.CONCAT:
        binary = (RegExp2) this;
        return new RegExp2(type, binary.r1.normaliseMacros(m), binary.r2.normaliseMacros(m));

      case sym.STAR:
      case sym.PLUS:
      case sym.QUESTION:
      case sym.BANG:
      case sym.TILDE:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        return new RegExp1(type, content.normaliseMacros(m));

      case sym.CCLASS:
      case sym.CCLASSNOT:
        {
          unary = (RegExp1) this;
          List<RegExp> contents = (List<RegExp>) unary.content;
          List<RegExp> newContents = new ArrayList<RegExp>(contents.size());
          for (RegExp r : contents) {
            RegExp n = r.normaliseMacros(m);
            newContents.add(n);
          }
          return new RegExp1(type, newContents);
        }

      case sym.CCLASSOP:
        unary = (RegExp1) this;
        binary = (RegExp2) unary.content;
        RegExp l = binary.r1.normaliseMacros(m);
        RegExp r = binary.r2.normaliseMacros(m);
        return new RegExp1(type, new RegExp2(binary.type, l, r));

      case sym.STRING:
      case sym.STRING_I:
      case sym.CHAR:
      case sym.CHAR_I:
      case sym.PRIMCLASS:
      case sym.PRECLASS:
      case sym.UNIPROPCCLASS:
        unary = (RegExp1) this;
        return new RegExp1(type, unary.content);

      case sym.MACROUSE:
        unary = (RegExp1) this;
        return m.getDefinition((String) unary.content).normaliseMacros(m);

      default:
        throw new RegExpException(this);
    }
  }

  /**
   * Normalise the regular expression to eliminate compound character class expression (compute
   * their content).
   *
   * @param f the spec file containing the regular expression (for error reporting)
   * @param line the line number of the regular expression (for error reporting)
   * @return a regexp where all char classes are primitive {@link IntCharSet} classes.
   */
  @SuppressWarnings("unchecked")
  public final RegExp normaliseCCLs(File f, int line) {
    RegExp1 unary;
    RegExp2 binary;
    RegExp content;

    try {
      switch (type) {
        case sym.BAR:
        case sym.CONCAT:
          binary = (RegExp2) this;
          return new RegExp2(
              type, binary.r1.normaliseCCLs(f, line), binary.r2.normaliseCCLs(f, line));

        case sym.STAR:
        case sym.PLUS:
        case sym.QUESTION:
        case sym.BANG:
        case sym.TILDE:
          unary = (RegExp1) this;
          content = (RegExp) unary.content;
          return new RegExp1(type, content.normaliseCCLs(f, line));

        case sym.STRING:
        case sym.STRING_I:
        case sym.CHAR:
        case sym.CHAR_I:
        case sym.PRIMCLASS:
          unary = (RegExp1) this;
          return new RegExp1(type, unary.content);

        case sym.CCLASS:
        case sym.CCLASSNOT:
          {
            unary = (RegExp1) this;
            List<RegExp> contents = (List<RegExp>) unary.content;
            IntCharSet set = new IntCharSet();
            for (RegExp r : contents) {
              RegExp1 n = checkPrimClass(r.normaliseCCLs(f, line));
              set.add((IntCharSet) n.content);
            }
            return new RegExp1(
                sym.PRIMCLASS, type == sym.CCLASS ? set : IntCharSet.complementOf(set));
          }

        case sym.CCLASSOP:
          unary = (RegExp1) this;
          binary = (RegExp2) unary.content;
          RegExp1 l = checkPrimClass(binary.r1.normaliseCCLs(f, line));
          IntCharSet setl = (IntCharSet) l.content;
          RegExp1 r = checkPrimClass(binary.r2.normaliseCCLs(f, line));
          IntCharSet setr = (IntCharSet) r.content;
          IntCharSet set = performClassOp(binary.type, setl, setr, this);
          return new RegExp1(sym.PRIMCLASS, set);

        default:
          throw new RegExpException(this);
      }
    } catch (CharClassException e) {
      Out.error(f, ErrorMessages.NOT_CHARCLASS, line, -1);
      throw new GeneratorException(e);
    }
  }

  /**
   * Expand pre-defined character classes into primitive IntCharSet classes.
   *
   * @param cache memoized pre-defined character class expansions
   * @param cl character class partitions
   * @return the expanded regular expression
   */
  @SuppressWarnings("unchecked")
  public RegExp expandPreClasses(Map<Integer, IntCharSet> cache, CharClasses cl, boolean caseless) {
    RegExp1 unary;
    RegExp2 binary;
    RegExp content;

    switch (type) {
      case sym.BAR:
      case sym.CONCAT:
        binary = (RegExp2) this;
        return new RegExp2(
            type,
            binary.r1.expandPreClasses(cache, cl, caseless),
            binary.r2.expandPreClasses(cache, cl, caseless));

      case sym.STAR:
      case sym.PLUS:
      case sym.QUESTION:
      case sym.BANG:
      case sym.TILDE:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        return new RegExp1(type, content.expandPreClasses(cache, cl, caseless));

      case sym.CCLASS:
      case sym.CCLASSNOT:
        {
          unary = (RegExp1) this;
          List<RegExp> contents = (List<RegExp>) unary.content;
          List<RegExp> newContents = new ArrayList<RegExp>(contents.size());
          for (RegExp r : contents) {
            RegExp n = r.expandPreClasses(cache, cl, caseless);
            newContents.add(n);
          }
          return new RegExp1(type, newContents);
        }

      case sym.CCLASSOP:
        unary = (RegExp1) this;
        binary = (RegExp2) unary.content;
        RegExp l = binary.r1.expandPreClasses(cache, cl, caseless);
        RegExp r = binary.r2.expandPreClasses(cache, cl, caseless);
        return new RegExp1(type, new RegExp2(binary.type, l, r));

      case sym.PRECLASS:
        {
          unary = (RegExp1) this;
          IntCharSet set = getPreClass(cache, cl, (Integer) unary.content);
          return new RegExp1(sym.PRIMCLASS, set);
        }

      case sym.UNIPROPCCLASS:
        {
          unary = (RegExp1) this;
          IntCharSet set = cl.getUnicodeProperties().getIntCharSet((String) unary.content);
          if (caseless) {
            set = set.getCaseless(cl.getUnicodeProperties());
          }
          return new RegExp1(sym.PRIMCLASS, set);
        }

      case sym.STRING:
      case sym.STRING_I:
      case sym.CHAR:
      case sym.CHAR_I:
      case sym.PRIMCLASS:
        unary = (RegExp1) this;
        return new RegExp1(type, unary.content);

      default:
        throw new RegExpException(this);
    }
  }

  /**
   * Check whether a character is a member of the give char class type.
   *
   * @param type the type of the character class ({@link sym#JLETTERCLASS} or {@link
   *     sym#JLETTERDIGITCLASS})
   * @param c the character to check
   * @return true if the character is a member of the class
   */
  private static boolean checkJPartStart(int type, int c) {
    switch (type) {
      case sym.JLETTERCLASS:
        return Character.isJavaIdentifierStart(c);

      case sym.JLETTERDIGITCLASS:
        return Character.isJavaIdentifierPart(c);

      default:
        return false;
    }
  }

  /**
   * Compute and memoize a pre-defined character class.
   *
   * @param preclassCache memoized pre-defined character class expansions
   * @param charClasses character class partitions
   * @param type the type of the predefined character class
   * @return the expanded IntCharSet for the class
   */
  private static IntCharSet getPreClass(
      Map<Integer, IntCharSet> preclassCache, CharClasses charClasses, int type) {
    IntCharSet result = preclassCache.get(type);
    if (null == result) {
      UnicodeProperties unicodeProperties = charClasses.getUnicodeProperties();
      switch (type) {
        case sym.LETTERCLASS:
          result = unicodeProperties.getIntCharSet("L");
          break;

        case sym.DIGITCLASS:
          result = unicodeProperties.getIntCharSet("Nd");
          break;

        case sym.DIGITCLASSNOT:
          IntCharSet digits = unicodeProperties.getIntCharSet("Nd");
          result = IntCharSet.ofCharacterRange(0, unicodeProperties.getMaximumCodePoint());
          result.sub(digits);
          break;

        case sym.UPPERCLASS:
          // "Uppercase" is more than Uppercase_Letter, but older Unicode
          // versions don't have this definition - check for "Uppercase",
          // then fall back to Uppercase_Letter (Lu) if it does not exist.
          result = unicodeProperties.getIntCharSet("Uppercase");
          if (null == result) {
            result = unicodeProperties.getIntCharSet("Lu");
          }
          break;

        case sym.LOWERCLASS:
          // "Lowercase" is more than Lowercase_Letter, but older Unicode
          // versions don't have this definition - check for "Lowercase",
          // then fall back to Lowercase_Letter (Ll) if it does not exist.
          result = unicodeProperties.getIntCharSet("Lowercase");
          if (null == result) {
            result = unicodeProperties.getIntCharSet("Ll");
          }
          break;

        case sym.WHITESPACECLASS:
          // Although later versions do, Unicode 1.1 does not have the
          // "Whitespace" definition - check for "Whitespace", then fall back
          // to "Space_separator" (Zs) if it does not exist.
          result = unicodeProperties.getIntCharSet("Whitespace");
          if (null == result) {
            result = unicodeProperties.getIntCharSet("Zs");
          }
          break;

        case sym.WHITESPACECLASSNOT:
          // Although later versions do, Unicode 1.1 does not have the
          // "Whitespace" definition - check for "Whitespace", then fall back
          // to "Space_separator" (Zs) if it does not exist.
          IntCharSet whitespaceClass = unicodeProperties.getIntCharSet("Whitespace");
          if (null == whitespaceClass) {
            whitespaceClass = unicodeProperties.getIntCharSet("Zs");
          }
          result = IntCharSet.ofCharacterRange(0, unicodeProperties.getMaximumCodePoint());
          result.sub(whitespaceClass);
          break;

        case sym.WORDCLASS:
          {
            // UTR#18: \w = [\p{alpha}\p{gc=Mark}\p{digit}\p{gc=Connector_Punctuation}]
            IntCharSet alphaClass = unicodeProperties.getIntCharSet("Alphabetic");
            if (null == alphaClass) {
              // For Unicode 1.1, substitute "Letter" (L) for "Alphabetic".
              alphaClass = unicodeProperties.getIntCharSet("L");
            }
            IntCharSet markClass = unicodeProperties.getIntCharSet("M");
            IntCharSet digitClass = unicodeProperties.getIntCharSet("Nd");
            IntCharSet connectorPunctClass = unicodeProperties.getIntCharSet("Pc");
            if (null == connectorPunctClass) {
              // For Unicode 1.1, substitute "_" for "Connector_Punctuation".
              connectorPunctClass = IntCharSet.ofCharacter('_');
            }
            result = IntCharSet.copyOf(alphaClass);
            result.add(markClass);
            result.add(digitClass);
            result.add(connectorPunctClass);
            break;
          }

        case sym.WORDCLASSNOT:
          {
            // UTR#18: \W = [^\p{alpha}\p{gc=Mark}\p{digit}\p{gc=Connector_Punctuation}]
            IntCharSet alphaClass = unicodeProperties.getIntCharSet("Alphabetic");
            if (null == alphaClass) {
              // For Unicode 1.1, substitute "Letter" (L) for "Alphabetic".
              alphaClass = unicodeProperties.getIntCharSet("L");
            }
            IntCharSet markClass = unicodeProperties.getIntCharSet("M");
            IntCharSet digitClass = unicodeProperties.getIntCharSet("Nd");
            IntCharSet connectorPunctClass = unicodeProperties.getIntCharSet("Pc");
            if (null == connectorPunctClass) {
              // For Unicode 1.1, substitute "_" for "Connector_Punctuation".
              connectorPunctClass = IntCharSet.ofCharacter('_');
            }
            IntCharSet wordClass = IntCharSet.copyOf(alphaClass);
            wordClass.add(markClass);
            wordClass.add(digitClass);
            wordClass.add(connectorPunctClass);
            result = IntCharSet.ofCharacterRange(0, unicodeProperties.getMaximumCodePoint());
            result.sub(wordClass);
            break;
          }

        case sym.JLETTERCLASS:
        case sym.JLETTERDIGITCLASS:
          result = new IntCharSet();

          int c = 0;
          int start = 0;
          int last = charClasses.getMaxCharCode();

          boolean prev, current;

          prev = checkJPartStart(type, 0);

          for (c = 1; c < last; c++) {

            current = checkJPartStart(type, c);

            if (!prev && current) start = c;
            if (prev && !current) {
              result.add(new Interval(start, c - 1));
            }

            prev = current;
          }

          // the last iteration is moved out of the loop to
          // avoid an endless loop if last == maxCharCode and
          // last+1 == 0
          current = checkJPartStart(type, c);

          if (!prev && current) result.add(new Interval(c, c));
          if (prev && current) result.add(new Interval(start, c));
          if (prev && !current) result.add(new Interval(start, c - 1));
          break;

        default:
          throw new CharClassException("Unknown predefined char class type: " + type);
      }

      preclassCache.put(type, result);
    }

    return result;
  }

  /**
   * Make character class partitions based on the classes mentioned in this regexp.
   *
   * <p>Assumption: regexp is normalised.
   */
  public final void makeCCLs(CharClasses c, boolean caseless) {
    RegExp1 unary;
    RegExp2 binary;
    RegExp content;

    switch (type) {
      case sym.BAR:
      case sym.CONCAT:
        binary = (RegExp2) this;
        binary.r1.makeCCLs(c, caseless);
        binary.r2.makeCCLs(c, caseless);
        return;

      case sym.STAR:
      case sym.PLUS:
      case sym.QUESTION:
      case sym.BANG:
      case sym.TILDE:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        content.makeCCLs(c, caseless);
        return;

      case sym.CHAR:
      case sym.CHAR_I:
        Integer ch = (Integer) ((RegExp1) this).content;
        c.makeClass(ch, caseless);
        return;

      case sym.STRING:
      case sym.STRING_I:
        String str = (String) ((RegExp1) this).content;
        c.makeClass(str, caseless);
        return;

      case sym.PRIMCLASS:
        unary = (RegExp1) this;
        IntCharSet set = (IntCharSet) unary.content;
        c.makeClass(set, Options.jlex && caseless);
        return;

      default:
        throw new CharClassException("makeCCLs: unexpected regexp " + this);
    }
  }

  /**
   * Creates a new regexp that matches the reverse text of this one.
   *
   * @return the reverse regexp
   */
  public final RegExp rev() {
    RegExp1 unary;
    RegExp2 binary;
    RegExp content;

    switch (type) {
      case sym.BAR:
        binary = (RegExp2) this;
        return new RegExp2(sym.BAR, binary.r1.rev(), binary.r2.rev());

      case sym.CONCAT:
        binary = (RegExp2) this;
        return new RegExp2(sym.CONCAT, binary.r2.rev(), binary.r1.rev());

      case sym.STAR:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        return new RegExp1(sym.STAR, content.rev());

      case sym.PLUS:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        return new RegExp1(sym.PLUS, content.rev());

      case sym.QUESTION:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        return new RegExp1(sym.QUESTION, content.rev());

      case sym.BANG:
        unary = (RegExp1) this;
        content = (RegExp) unary.content;
        return new RegExp1(sym.BANG, content.rev());

      case sym.TILDE:
        content = resolveTilde();
        return content.rev();

      case sym.STRING:
      case sym.STRING_I:
        unary = (RegExp1) this;
        return new RegExp1(unary.type, revString((String) unary.content));

      case sym.CHAR:
      case sym.CHAR_I:
      case sym.PRIMCLASS:
        unary = (RegExp1) this;
        return new RegExp1(unary.type, unary.content);

      default:
        throw new RegExpException(this);
    }
  }
}
