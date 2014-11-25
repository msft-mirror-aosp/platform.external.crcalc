/*
 * Copyright (c) 2001 Hewlett-Packard Company
 *
 * Permission to use, copy, modify, distribute and sell this software
 * and its documentation for any purpose is hereby granted without fee,
 * provided that the above copyright notice appear in all copies and
 * that both that copyright notice and this permission notice appear
 * in supporting documentation.
 * HEWLETT-PACKARD COMPANY MAKES NO REPRESENTATIONS ABOUT THE SUITABILITY
 * OF THIS SOFTWARE FOR ANY PURPOSE.  IT IS PROVIDED "AS IS" WITHOUT
 * EXPRESS OR IMPLIED WARRANTY.
 */

//
// This is a simple command-line calculator driver for the Java constructive
// reals library.  In most cases, the GUI version CRCalc.java will be
// preferable.  But this version can be built without awt support, making
// it buildable, e.g. with early versions of gcj.
// This driver is completely single-threaded.
// No attempt is made to handle nonterminating computations.  The user
// will have to kill the process if he/she starts one.
//
// Note that this was developed at Hewlett-Packard, and
// not part of the package originally distributed by SGI.
//
//	Author: Hans-J. Boehm (Hans_Boehm@hp.com, boehm@acm.org)
//

import com.sgi.math.CR;
import com.sgi.math.UnaryCRFunction;
import java.math.BigInteger;
import java.io.DataInputStream;

class rpn_calc {
    static String help_string = "This is a basic RPN calculator.\n" +
	"Each input line consists of decimal numbers separated by\n" +
	"operators or spaces.  The calculator prints the top of stack\n" +
	"(if any) after processing each line.  A line consisting of\n" +
	"just an \"a\" will print the entire stack.\n\n" +
	"Operators:\n" +
	"+ (add); - (subtract); * (multiply); / (divide); ^ (exponentiate)\n" +
	"e (exp); l (ln); s (sin); S (asin); c (cos); C (acos);\n" +
	"t (tan); T (atan); r (sqrt) ~ (negate)\n" +
	"q (copy top); i (interchange top two); [n]> more prec. " +
	"[n]< less prec.\n" +
	"p (enter PI); h (print help); d (delete entry); x (exit)\n" +
	"stack overflow and nontermination (e.g. division by zero) are NOT\n" +
	"handled correctly in this version.\n";
	
    static BigInteger input = BigInteger.valueOf(0);
    static int fraction_digits = 0;
    static boolean point_entered = false;
    static boolean entry_pending = false;
    static void process_digit(int d) {
	input = input.multiply(BigInteger.valueOf(10));
	input = input.add(BigInteger.valueOf(d));
	if (point_entered) fraction_digits++;
	entry_pending = true;
    }
    static void process_point() {
	point_entered = true;
    }
    static void clear_entry() {
	input = BigInteger.valueOf(0);
	fraction_digits = 0;
	point_entered = false;
	entry_pending = false;
    }
    static CR input_value() {
	CR divisor = CR.valueOf(BigInteger.valueOf(10).pow(fraction_digits));
	return CR.valueOf(input).divide(divisor);
    }
    static void enter() {
	if (entry_pending) push(input_value());
	clear_entry();
    }
    static CR memory = CR.valueOf(0);
    static DataInputStream in = new DataInputStream(System.in);
    static CR stack[] = new CR[1000];
    static int stack_ptr = 0;	/* Number of valid entries. */
    static void push(CR x) { stack[stack_ptr] = x;  stack_ptr++; }
    static CR top() {
      return stack[stack_ptr-1];
    }
    static CR second() {
      return stack[stack_ptr-2];
    }
    static boolean require(int n) {
      if (stack_ptr < n) {
	System.out.println("Stack underflow");
	return false;
      } else {
        return true;
      }
    }
    static void doUnary(UnaryCRFunction f) {
      enter();
      if (require(1)) {
	stack[stack_ptr-1] = f.execute(top());
      }
    }
    
    static char read_char() {
	try {
	    return (char)in.readByte(); 
	} catch(java.io.IOException e) {
	    return 'q';
	} 
    }

    static int ndigits = 2;

    public static void main(String argv[]) {
        char c;

	for(;;) {
	  c = read_char();
	  if (Character.isDigit(c)) {
	    process_digit(Character.digit(c, 10));
	  } else if (c == '.') {
	    process_point();
	  } else {
	    if (entry_pending && c != '<' && c != '>') enter();
	    switch(c) {
		case '\n':
	            if (0 != stack_ptr)
			System.out.println(top().toString(ndigits));
		    break;
		case ' ':
		    break;
		case '+':
		    if (require(2)) {
		      stack[stack_ptr-2] = second().add(top());
		      -- stack_ptr;
		    }
		    break;
		case '-':
		    if (require(2)) {
		      stack[stack_ptr-2] = second().subtract(top());
		      -- stack_ptr;
		    }
		    break;
		case '*':
		    if (require(2)) {
		      stack[stack_ptr-2] = second().multiply(top());
		      -- stack_ptr;
		    }
		    break;
		case '/':
		    if (require(2)) {
		      stack[stack_ptr-2] = second().divide(top());
		      -- stack_ptr;
		    }
		    break;
		case '^':
		    if (require(2)) {
		      CR base = stack[stack_ptr-2];
		      CR exponent = stack[stack_ptr-1];
		      CR result = base.ln().multiply(exponent).exp();
		      stack[stack_ptr-2] = result;
		      -- stack_ptr;
		    }
		    break;
		case '~':
		    doUnary(UnaryCRFunction.negateFunction);
		    break;
		case 'e':
		case 'E':
		    doUnary(UnaryCRFunction.expFunction);
		    break;
		case 'l':
		case 'L':
		    doUnary(UnaryCRFunction.lnFunction);
		    break;
		case 'r':
		case 'R':
		    doUnary(UnaryCRFunction.sqrtFunction);
		    break;
	     	case 't':
		    doUnary(UnaryCRFunction.tanFunction);
		    break;
	     	case 'T':
		    doUnary(UnaryCRFunction.atanFunction);
		    break;
	     	case 'c':
		    doUnary(UnaryCRFunction.cosFunction);
		    break;
	     	case 'C':
		    doUnary(UnaryCRFunction.acosFunction);
		    break;
	     	case 's':
		    doUnary(UnaryCRFunction.sinFunction);
		    break;
	     	case 'S':
		    doUnary(UnaryCRFunction.asinFunction);
		    break;
		case 'p':
		case 'P':
		    push(CR.PI);
		    break;
		case '=':
		    if (require(1)) {
		      memory = top();
		      System.out.print("\nSaving: ");
		    }
		    break;
		case 'g':
		    push(memory);
		    break;	
		case '>':
		    if (entry_pending) {
		      ndigits += input.intValue();
		      clear_entry();
		    } else {
		      ndigits += 10;
		    }
		    break;
		case '<':
		    if (entry_pending) {
		      ndigits -= input.intValue();
		      clear_entry();
		    } else {
		      ndigits -= 10;
		    }
		    if (ndigits < 0) ndigits = 0;
		    break;
		case 'a':
		case 'A':
		    for (int i = 0; i < stack_ptr - 1; ++i) {
			System.out.println(stack[i].toString(ndigits));
		    }
		    break;
		case 'd':
		case '\b':
		    if (require(1))
		      --stack_ptr;
		    break;
		case 'q':
		case 'Q':
		    if (require(1))
		      push(top());
		    break;
		case 'i':
		case 'I':
		    if (require(2)) {
		      CR tmp = top();
		      stack[stack_ptr-1] = second();
		      stack[stack_ptr-2] = tmp;
		    }
		case 'h':
		case 'H':
		    System.out.print(help_string);
		    break;
		case 'x':
		case 'X':
		    return;
	    } /* switch */
	  }
	} 
    }
}

