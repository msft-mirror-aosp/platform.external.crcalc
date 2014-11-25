// Copyright © 1999, Silicon Graphics, Inc. -- ALL RIGHTS RESERVED 
// 
// Permission is granted free of charge to copy, modify, use and distribute
// this software  provided you include the entirety of this notice in all
// copies made.
// 
// THIS SOFTWARE IS PROVIDED ON AN AS IS BASIS, WITHOUT WARRANTY OF ANY
// KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING, WITHOUT LIMITATION,
// WARRANTIES THAT THE SUBJECT SOFTWARE IS FREE OF DEFECTS, MERCHANTABLE, FIT
// FOR A PARTICULAR PURPOSE OR NON-INFRINGING.   SGI ASSUMES NO RISK AS TO THE
// QUALITY AND PERFORMANCE OF THE SOFTWARE.   SHOULD THE SOFTWARE PROVE
// DEFECTIVE IN ANY RESPECT, SGI ASSUMES NO COST OR LIABILITY FOR ANY
// SERVICING, REPAIR OR CORRECTION.  THIS DISCLAIMER OF WARRANTY CONSTITUTES
// AN ESSENTIAL PART OF THIS LICENSE. NO USE OF ANY SUBJECT SOFTWARE IS
// AUTHORIZED HEREUNDER EXCEPT UNDER THIS DISCLAIMER.
// 
// UNDER NO CIRCUMSTANCES AND UNDER NO LEGAL THEORY, WHETHER TORT (INCLUDING,
// WITHOUT LIMITATION, NEGLIGENCE OR STRICT LIABILITY), CONTRACT, OR
// OTHERWISE, SHALL SGI BE LIABLE FOR ANY DIRECT, INDIRECT, SPECIAL,
// INCIDENTAL, OR CONSEQUENTIAL DAMAGES OF ANY CHARACTER WITH RESPECT TO THE
// SOFTWARE INCLUDING, WITHOUT LIMITATION, DAMAGES FOR LOSS OF GOODWILL, WORK
// STOPPAGE, LOSS OF DATA, COMPUTER FAILURE OR MALFUNCTION, OR ANY AND ALL
// OTHER COMMERCIAL DAMAGES OR LOSSES, EVEN IF SGI SHALL HAVE BEEN INFORMED OF
// THE POSSIBILITY OF SUCH DAMAGES.  THIS LIMITATION OF LIABILITY SHALL NOT
// APPLY TO LIABILITY RESULTING FROM SGI's NEGLIGENCE TO THE EXTENT APPLICABLE
// LAW PROHIBITS SUCH LIMITATION.  SOME JURISDICTIONS DO NOT ALLOW THE
// EXCLUSION OR LIMITATION OF INCIDENTAL OR CONSEQUENTIAL DAMAGES, SO THAT
// EXCLUSION AND LIMITATION MAY NOT APPLY TO YOU.
// 
// These license terms shall be governed by and construed in accordance with
// the laws of the United States and the State of California as applied to
// agreements entered into and to be performed entirely within California
// between California residents.  Any litigation relating to these license
// terms shall be subject to the exclusive jurisdiction of the Federal Courts
// of the Northern District of California (or, absent subject matter
// jurisdiction in such courts, the courts of the State of California), with
// venue lying exclusively in Santa Clara County, California. 

// This is version 0.5
// Version 0.4 was the original version distributed by SGI.
// Version 0.5 adds a test for tan(<odd multiple of pi>)
//		(Suggested by Dan Grayson, implemented by Hans_Boehm@hp.com)

import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import com.sgi.math.CR;
import com.sgi.math.UnaryCRFunction;
import com.sgi.math.AbortedError;
import com.sgi.math.PrecisionOverflowError;
import java.math.BigInteger;

// A message window with an OK or YES/NO prompt.
class calc_msg extends Frame implements ActionListener {
    Button ok_button;
    Button yes_button;
    Button no_button;
    Dialog d;
    Panel p;
    Component t;	// Widget containing test
    static final int label_limit = 50;
    boolean ask;
    boolean answer;
    boolean done;

    calc_msg(String S, boolean a) {
	ask = a;
	int len = S.length();
	done = false;
	d = new Dialog(this, "CRCalc information");
	if (len > label_limit) {
	    t = new TextArea(S);
	} else {
	    t = new Label(S);
	}
	if (ask) {
	    yes_button = new Button("YES");
	    yes_button.addActionListener(this);
	    no_button = new Button("NO");
	    no_button.addActionListener(this);
	} else {
	    ok_button = new Button("OK");
	    ok_button.addActionListener(this);
	}
	p = new Panel();
	p.setLayout(new GridLayout(1, 3));
	if (ask) {
	    p.add(yes_button);
	} else {
	    p.add(new Label());
	}
   	p.add(new Label());
	if (ask) {
	    p.add(no_button);
	} else {
	    p.add(ok_button);
	}
	d.add("North", t);
	d.add("South", p);
	if (len > label_limit) {
	    d.setSize(520,300);
	} else {
	    d.setSize(400, 100);
	}
	d.show();
    }
    public synchronized void actionPerformed(ActionEvent e) {
	answer = (e.getSource() == yes_button);
	done = true;
	dispose();
	notify();
    }
    public synchronized boolean get_answer() {
	while (!done) {
	    try {
		wait();
	    } catch(InterruptedException e) { return false; }
	}
	return answer;
    }
}

// The queue of commands waiting to be executed.  Used to communicate
// with the worker thread.
class command_queue_entry {
    char command;
    command_queue_entry next;
    command_queue_entry(char c) {
	command = c;
	next = null;
    }
}

// Queue of commands waiting to be executed by the worker thread.
// Duplicate refresh commands are quietly dropped to improve performance
// on slower machines.
class command_queue {
    static final char noop_command = (char)0;
    static final char refresh_command = ',';
    volatile boolean please_exit;
    command_queue_entry first;
    command_queue_entry last;
    command_queue() {
	first = null;
	last = null;
    }
    synchronized void add(char c) {
	command_queue_entry new_last = new command_queue_entry(c);
	if (refresh_command == c) {
	    // Eliminate duplicate refresh commands
	    for (command_queue_entry p = first; p != null; p = p.next) {
		if (p.command == refresh_command) {
		    p.command = noop_command;
		}
	    }
	}
	if (null == first) {
	    first = new_last;
	    last = new_last;
	} else {
	    last.next = new_last;
	    last = new_last;
	}
	notify();
    }
    synchronized char get() {
	char result;
	do {
	  while (null == first) {
            try {
	      wait(1000);
	    } catch (InterruptedException e) {return noop_command;}
	    if (please_exit) { return noop_command; }
	  }
	  result = first.command;
	  first = first.next;
	  if (null == first) last = null;
	} while (result == noop_command);
	return result;
    }
}

// This is a gross hack to force most VMs that don't normally timeslice to
// do so.  Without timeslicing a stop button push will never be processed.
// (Actually, it should be, since the worker runs at lower priority.
// Empirically, that doesn't seem to matter much, probably due to awt
// issues.)  Unnessary for VMs that timeslice correctly, but we can't
// easily identify those.
class time_slicer implements Runnable {
    volatile boolean please_exit;

    public void run() {
	try {
	  for (;;) {
 	    if (please_exit) break;
	    Thread.sleep(100);
	  }
	} catch(InterruptedException e) {}
    }
}

// Radian to/from degree conversion functions.
class to_degrees_class extends UnaryCRFunction {
    CR multiplier = CR.valueOf(180).divide(CR.PI);
    public CR execute(CR x) {
	return multiplier.multiply(x);
    }
}

class from_degrees_class extends UnaryCRFunction {
    CR multiplier = CR.PI.divide(CR.valueOf(180));
    public CR execute(CR x) {
	return multiplier.multiply(x);
    }
}

public class CRCalc extends Applet
implements AdjustmentListener, ActionListener,
	   KeyListener, TextListener, ItemListener, Runnable {

    static final String help_text =
	"This is a calculator that operates on \"exact\" real numbers.\n" +
	"More precisely, numbers are represented internally so that\n" +
	"they can be evaluated to any needed precision.  As a\n" +
	"result, the displayed numbers are accurate to an error\n" +
	"of strictly less than 1 digit in the last displayed\n" +
	"digit, no matter how the number was computed.  Subexpressions\n" +
	"are evaluated to enough precision to ensure that cumulative\n" +
	"rounding errors remain invisible.\n" +
	"\n" +
	"OPERATION:\n" +
	"The calculator uses \"reverse Polish notation\".\n" +
	"To compute 1 + 2, enter <1><enter><2><+>\n" +
	"Digits may either be typed into the main display window\n" +
	"from the keyboard, or may be entered with the calculator\n" +
	"buttons.  The operation buttons also have keyboard\n" +
	"equivalents, which will appear in the history window near\n" +
	"the bottom of the calculator.  Additionally, either <space>\n" +
	"or <enter> a.k.a. <return> may be used as <enter> equivalents.\n\n" +
	"The main display window displays all values on the current\n" +
	"operand stack.  Operations apply to the the value(s) nearest\n" +
	"the bottom of the screen.  To add a long sequence of numbers,\n" +
	"it is often most convenient to enter all of them, separated\n" +
	"by <enter>, and then add them by repeatedly pushing <+>.\n\n" +
	"The history window at the bottom of the calculator can be\n" +
	"useful for reviewing past operations, or reexeuting them.\n\n" +
	"The small text window under the main display gives the place\n" +
	"value of the least significant displayed digit.  If it\n" +
	"displays -100, then right most digit is the 100th digit to the\n" +
	"right of the decimal point, with place value of 10^(-100).\n" +
	"The display can be shifted right or left by either editing the\n" +
	"number in the small window, or by using the adjacent scroll bar.\n" +
	"\n" +
	"CONTROL BUTTONS:\n" +
	"The calculator includes a column of six control buttons.\n" +
	"One of them is the \"HELP\" button that generated this text.\n" +
	"The others are:\n" +
	"STOP:\taborts the current computation.  This may cause\n" +
	"\tsome partial results to be lost (see \"replay\").  It\n" +
	"\tis quite easy to start the calculator on a computation\n" +
	"\tthat will never finish, or finish only when it runs out\n" +
	"\tmemory.  The simplest way to do this is to divide by 0.\n" +
	"\tIn this case you may need to use the STOP button.  Many\n" +
	"\toperations will warn you if it appears likely that you \n" +
	"\twill need to use the stop button.  It is difficult for\n" +
	"\tthe calculator to determine whether an arbitrarily\n" +
	"\tcomputed number is exactly zero.  It doesn't even try.\n" +
	"\t(This is undecidable for arbitrary constructive reals.\n" +
	"\tIt is probably just impractically expensive for the\n" +
	"\toperations provided by the calculator.\n" +
	"\tThe stop button also serves as an indication that a\n" +
	"\tcalculation is in progress.  A lower case \"stop\" label\n" +
	"\ton the button indicates the calculator is idle.  An\n" +
	"\tupper case \"STOP!\" label means that a caclulation is\n" +
	"\tin progress, and the button is functional.\n" +
	"set prec: Set the displayed precision (place value of\n" +
	"\tleast displayed digit) to be the number on top of\n" +
	"\tstack, and pop the stack.  Entering -10 and then clicking\n" +
	"\t<set prec> will display 10 digits to the right of the\n" + 
	"\tdecimal point.  This is an alternative to the scroll bar\n" +
	"\tand scroll window.\n" +
	"replay: Reexecutes the commands the selected in the\n" +
	"\thistory window.  Select the text before pressing the button.\n" +
	"\tSince the history window is editable, on many systems this\n" +
	"\talso gives a way to copy numbers into the calculator: First\n" +
	"\tpaste the number into the history window, then select it,\n" +
	"\tand press replay.\n" +
	"write: Write the top of stack to the Java console.  The\n" +
	"\tnumber is written out to the current precision.  Digits\n" +
	"\tto the left of the display window are included.  (Many\n" +
	"\tbrowsers will allow the result from the Java console to\n" +
	"\tbe displayed and pasted elsewhere.  Direct clipboard access\n" +
	"\tfrom Java applets is normally disallowed for security\n" +
	"\treasons.)\n" +
	"base 16: Switches to base 16 mode if pushed or checked.\n"+
	"\tAffects both input and display.  To convert\n" +
	"\tbetween bases, enter the number, then switch.\n" +
	"\tHexadecimal digits between \"a\" and \"f\" must be entered\n" +
	"\twith the keyboard and input focus on the display window\n" +
	"degrees: Alters the behavior of the trignonometric\n" +
	"\tfunctions to measure angles in either degrees or radians\n" +
	"\n" +
	"EXAMPLES:\n" +
	"Ramanujan's number (exp(pi*sqrt(163))) is entered as\n" +
	"<pi><1><6><3><sqrt><*><exp>\n" +
	"or from the keyboard as \"p163r*x\"\n" +
	"Note that the result is not an integer.\n" +
	"\n" +
	"To see the difference between this and other calculators, try\n" +
	"ln(pi + exp(-234) - pi), which can be entered as\n" +
	"<pi><2><3><4><+/-><exp><+><pi><-><ln>\n"
	;
    static final int stack_size = 300;
    int display_width;
    int display_char_width;
    final int initial_prec = -22;
    static final int max_history_len = 200;
    static final BigInteger big0 = BigInteger.valueOf(0);
    static final BigInteger big10 = BigInteger.valueOf(10);
    static final BigInteger big16 = BigInteger.valueOf(16);
    static final to_degrees_class to_degrees = new to_degrees_class();
    static final from_degrees_class from_degrees = new from_degrees_class();

    /* This thread actually executes operations, so that we can stop */
    /* it if necessary.						     */
    Thread worker;

    /* Extra high priority thread to force time slicing.	*/
    time_slicer ts;
    Thread ts_thread;

    /* Calculator state, updated only by worker thread. */
    CR stack[] = new CR[stack_size];	// top at higher indicees.
    int stack_ptr;			// number of valid entries.
    CR mem = CR.valueOf(0);	// memory contents.
    int current_prec; 		// Weight of least displayed digit
				// is 10**current_prec.
				// This is a final array of one element
				// so that it can be changed from an
				// inner class.
    BigInteger current_entry;   // Number entered so far if decimal
				// point had not been entered.
    boolean point_entered;	// Was decimal point entered?
    int fraction_digits;	// number of digits to the right of
				// decimal point.
    int digits_entered;		// Count of number of digits entered.
    int current_base;		// Entry and display radix.
    boolean degree_mode;	// Currently using degrees as angle
				// measure.
    // Current angle conversion function.
    // In degree mode these perform the appropriate scaling.
    UnaryCRFunction input_converter;
    UnaryCRFunction output_converter;

    BigInteger current_big_base;
    String current_entry_string = new String();
    String history_contents = new String();

    // Queue of commands to be executed by worker.
    command_queue cq;

    // Widgets that make up the calculator UI
    TextArea display;
    final Scrollbar prec_scroll = new Scrollbar(Scrollbar.HORIZONTAL);
    final TextField prec_window = new TextField("", 10);
    TextArea history;
    Panel keypad;
    Panel control_panel;
    Panel keypanel;	// The above two combined, so they stay together.
    Panel prec_scroll_panel;
    Button add_button;
    Button multiply_button;
    Button subtract_button;
    Button divide_button;
    Button power_button;
    Button dot_button;
    Button enter_button;
    Button negate_button;
    Button inverse_button;
    Button sqrt_button;
    Button exp_button;
    Button ln_button;
    Button pi_button;
    Button digit_button[] = new Button[10];
    Button sin_button;
    Button cos_button;
    Button tan_button;
    Button asin_button;
    Button acos_button;
    Button atan_button;
    Button help_button;
    Button stop_button;
    Button xchg_button;
    Button clear_button;
    Button clear_all_button;
    Button copy_button;
    Button store_button;
    Button recall_button;
    Checkbox base_box;
    Button replay_button;
    Button set_prec_button;
    // Button paste_button;
    Button write_button;
    Checkbox degree_box;

    // A helper function for refresh
    // Generate a String containing n blanks.
      private static String blanks(int n) {
	char[] a = new char[n];
	for (int i = 0; i < n; ++i) {
	    a[i] = ' ';
	}
        return new String(a);
      }    

    static final int extra_prec = 15; // Extra digits generated and then
				      // discarded.  This makes it unlikely
				      // that digits flip-flop as more
				      // precision is requested.

    // Recompute the display after a stack or precision change.
    // Should run only in worker thread, since it may fail to terminate.
    void refresh() {
	String display_contents = new String();
	
	for (int i = 0; i < stack_ptr; ++i) {
	    int convert_prec = -current_prec + extra_prec;
	    CR current_entry = stack[i];
	    if (convert_prec < 0) {
		BigInteger scale = current_big_base.pow(-convert_prec);
		CR cr_scale = CR.valueOf(scale);
		current_entry = current_entry.divide(cr_scale);
		convert_prec = 0;
	    }
	    String this_entry = current_entry.toString(convert_prec,
						       current_base);
	    int len = this_entry.length();
	    if (len <= extra_prec) {
		// Not this long
		this_entry = blanks(display_char_width - 3) + "-->";
	    } else {
	        int end_char = len - extra_prec;
	        if (end_char > display_char_width) {
		    this_entry = this_entry.substring(end_char
						      - display_char_width,
						      end_char);
	        } else {
		    this_entry = blanks(display_char_width - end_char)
		                 + this_entry.substring(0, end_char);
	        }
	    }
	    if (i != 0) display_contents += "\n";
	    display_contents += this_entry;
	}
        if (stack_ptr != 0) display_contents += "\n";
	if (digits_entered > 0) {
            display_contents += current_entry_string;
	}  
	display.setText(display_contents);
	display.setCaretPosition(display_contents.length());
    }
    
    void add_history_char(char c)
    {
	add_history_string(String.valueOf(c));
    }

    void add_history_string(String s)
    {
	String history_contents = history.getText() + s;
	int len = history_contents.length();
	if (len > max_history_len) {
	    history_contents = history_contents.substring(
					len - max_history_len);
	}
	history.setText(history_contents);
	history.setCaretPosition(history_contents.length());
    }

    // Clear the current partial entry.
    void clear_current_entry() {
	current_entry = big0;
	current_entry_string = "";
	point_entered = false;
	fraction_digits = 0;
	digits_entered = 0;
    }

    // Remove the last typed character.
    void delete_last_char() {
	if (point_entered) {
	    if (fraction_digits == 0) {
		point_entered = false;
	    } else {
		--fraction_digits;
		current_entry = current_entry.divide(current_big_base);
		--digits_entered;
	    }
	} else {
	    current_entry = current_entry.divide(current_big_base);
	    --digits_entered;
	}
	int len = current_entry_string.length();
	current_entry_string = current_entry_string.substring(0, len-1);
    }

    // Action when "clear entry" button is pushed.
    void clear_entry() {
	if (digits_entered == 0 && stack_ptr > 0) {
	    pop();
	} else {
	    clear_current_entry();
	}
    }

    void clear() {
	clear_entry();
	stack_ptr = 0;
    }

    void msg(String S) {
	new calc_msg(S, false);
    }

    // Display warning or error message.
    void warn(String S) {
	this.getToolkit().beep();
	msg(S);
    }

    // Ask the user a question.
    boolean ask(String S) {
	this.getToolkit().beep();
	calc_msg m = new calc_msg(S, true);
	return m.get_answer();
    }

    void push(CR x) {
	if (stack_ptr == stack_size) {
	    warn("Stack_overflow");
	} else {
	    stack[stack_ptr] = x;
	    ++stack_ptr;
	}
    }

    void pop() {
	if (stack_ptr > 0) --stack_ptr;
	stack[stack_ptr] = null;	// only a space optimization.
    }

    boolean check_unary() {
	if (digits_entered > 0 || point_entered) enter();
	if (stack_ptr < 1) {
	    warn("No argument for unary operation");
	    return false;
	} else {
	    return true;
	}
    }

    boolean check_binary() {
	if (digits_entered > 0 || point_entered) enter();
	if (stack_ptr < 2) {
	    warn("Too few arguments for binary operation");
	    return false;
	} else {
	    return true;
	}
    }

    void finish_binary(CR result, char h)
    {
   	pop(); pop();
	push(result);
	add_history_char(h);
	refresh();
    }

    void do_add() {
	if (!check_binary()) return;
	CR op2 = stack[stack_ptr-1];
	CR op1 = stack[stack_ptr-2];
	CR result = op1.add(op2);
	finish_binary(result, '+');
    }

    void do_multiply() {
	if (!check_binary()) return;
	CR op2 = stack[stack_ptr-1];
	CR op1 = stack[stack_ptr-2];
	CR result = op1.multiply(op2);
	finish_binary(result, '*');
    }

    void do_subtract() {
	if (!check_binary()) return;
	CR op2 = stack[stack_ptr-1];
	CR op1 = stack[stack_ptr-2];
	CR result = op1.subtract(op2);
	finish_binary(result, '-');
    }

    void do_divide() {
	if (!check_binary()) return;
	CR op2 = stack[stack_ptr-1];
	CR op1 = stack[stack_ptr-2];
	CR result = op1.divide(op2);
	finish_binary(result, '/');
    }

    void do_power() {
	CR result;
	if (!check_binary()) return;
	CR op2 = stack[stack_ptr-1];
	CR op1 = stack[stack_ptr-2];
	BigInteger int1 = op1.get_appr(0);
	BigInteger int2 = op2.get_appr(0);
	result = op1.ln().multiply(op2).exp();
	finish_binary(result, '^');
    }
    

    void do_exchange() {
	if (!check_binary()) return;
	CR tmp = stack[stack_ptr-1];
	stack[stack_ptr-1] = stack[stack_ptr-2];
	stack[stack_ptr-2] = tmp;
	add_history_char('i');
	refresh();
    }

    void do_unary(UnaryCRFunction f, char c) {
	if (!check_unary()) return;
	stack[stack_ptr - 1] = f.execute(stack[stack_ptr - 1]);
	add_history_char(c);
	refresh();
    }

    void do_pi() {
	if (digits_entered > 0 || point_entered) enter();
	push(CR.PI);
  	add_history_char('p');
	refresh();
    }

    void do_copy() {
	if (!check_unary()) return;
	push(stack[stack_ptr - 1]);
	add_history_char('q');
	refresh();
    }

    void do_store() {
	if (!check_unary()) return;
	mem = stack[stack_ptr - 1];
	add_history_char('=');
	refresh();	// May have done an implicit "enter"
    }

    void do_recall() {
	if (digits_entered > 0 || point_entered) enter();
	push(mem);
	add_history_char('g');
	refresh();
    }

    void add_digit(int d) {
	++digits_entered;
	if (point_entered) {
	    ++fraction_digits;
	}
	current_entry = current_entry.multiply(current_big_base);
	current_entry = current_entry.add(BigInteger.valueOf(d));
	if (d <= 9) {
	    current_entry_string += (char)('0' + d);
	} else {
	    current_entry_string += (char)('a' + d - 10);
	}
	refresh();
    }

    void add_period() {
	point_entered = true;
	fraction_digits = 0;
	current_entry_string += ".";
	add_history_char('.');
	refresh();
    }

    void enter() {
	CR entry;
	add_history_string(current_entry_string);
	if (point_entered && fraction_digits > 0) {
	  CR divisor = CR.valueOf(current_big_base.pow(fraction_digits));
	  entry = CR.valueOf(current_entry).divide(divisor);
	} else {
	  entry = CR.valueOf(current_entry);
	}
	push(entry);
	clear_current_entry();
    }

    final static int scroll_max = 100;
    final static int scroll_min = -100;

    static int prec_from_scroll_setting(int setting) {
 	int result;
	if (setting >= -50 && setting <= 50) {
	    return -setting;
	}
	if (setting > 50) return - (20 * (setting - 50) + 50);
	return  - (20 * (setting + 50) - 50);
    }

    static int scroll_setting_from_prec(int prec) {
 	int result;
	if (prec >= -50 && prec <= 50) {
	    return -prec;
	}
	if (prec > 50) return - ((prec - 50)/20 + 50);
	return  - ((prec + 50)/20 - 50);
    }

    // Common fixups when precision changes for any reason.
    void prec_changed(boolean set_text) {
	int scroll_val = scroll_setting_from_prec(current_prec);
	prec_scroll.setValue(scroll_val);
	if (set_text) prec_window.setText(String.valueOf(current_prec));
	enqueueKeyCommand(command_queue.refresh_command);
    }

    // Handle adjustment events for precision scroll bar.
    public void adjustmentValueChanged(AdjustmentEvent e) {
	int scroll_val = prec_scroll.getValue();
	current_prec = prec_from_scroll_setting(scroll_val);
	prec_changed(true);
    }

    // Text events for precision window
    public void textValueChanged(TextEvent e) {
	int new_prec;
	String new_text = prec_window.getText();
	if (new_text.compareTo("-") == 0) {
	    // incomplete entry
	    return;
	}
	try {
	    new_prec = Integer.parseInt(new_text);
	} catch (NumberFormatException exc) {
	    warn("Syntax error in precision");
	    prec_window.setText(String.valueOf(current_prec));
	    return;
	}
	if (current_prec != new_prec) {
	    current_prec = new_prec;
	    prec_changed(false);
	}
    }

//    void set_sizes() {
//	Dimension sz = this.getSize();
//	int width = sz.width;
//	int height = sz.height;
//        System.out.println("width = " + width + "height = " + height);
//        display_width = width - 50;
//	display_char_width = display_width/10;
//	prec_scroll.setSize(display_width, 20);
//	display.setSize(display_width, height/2);
//	display_char_width = display.getRows();
//        System.out.println("display_char_width = " + display_char_width);
//    }

    public void init() {
	// setBackground(new Color(0x6699ff));
	Font big_font = new Font("Helvetica", Font.BOLD, 18);
	Font medium_font = new Font("Helvetica", Font.PLAIN, 14);
	display_width = 450;
	display_char_width = 44;
	// Netscape TextAreas appear to be smaller than they're supposed
	// to be, at least under Irix.  Thus we pad unless told otherwise.
	int extra_columns = 0;
	int extra_rows = 0;
	int rows = 10;
	if (is_applet) {
	  String extra_rows_string = getParameter("extra_rows");
	  String rows_string = getParameter("rows");
	  String extra_columns_string = getParameter("extra_rows");
	  extra_columns = 2;
	  extra_rows = 1;
	  try {
	    if (null != extra_rows_string) {
	      extra_rows = Integer.parseInt(extra_rows_string);
	    }
	    if (null != extra_columns_string) {
	      extra_columns = Integer.parseInt(extra_columns_string);
	    }
	    if (null != rows_string) {
	      rows = Integer.parseInt(rows_string);
	    }
	  } catch (NumberFormatException e) {
	    System.out.println("Format error in display size parameter");
	  }
	}
  	display = new TextArea("", rows+extra_rows,
			       display_char_width + extra_columns,
			       TextArea.SCROLLBARS_VERTICAL_ONLY);
	// display.setBackground(new Color(0x99ccff));
	int font_size = 14;
  	if (null == display_font_name) {
	  display_font_name = getParameter("font");
	  if (null == display_font_name) display_font_name = "Courier";
	}
 	if (display_font_name.compareTo("Dialog") == 0) font_size = 16;
	Font display_font = new Font(display_font_name, Font.BOLD, font_size);
	FontMetrics display_metrics = this.getFontMetrics(display_font);
	if (display_metrics.charWidth(' ') != display_metrics.charWidth('9')) {
	  System.out.println("Requested display font not fixed with: "
			     + "Using Courier");
	  font_size = 14;
	  display_font = new Font("Courier", Font.BOLD, font_size);
	}
	display.setFont(display_font);
	display.setEditable(false);
	display.addKeyListener(this);
	keypad = new Panel();
  	keypad.setLayout(new GridLayout(7,5));
	control_panel = new Panel();
	control_panel.setLayout(new GridLayout(7,1));
	keypanel = new Panel();
	current_prec = initial_prec;
	prec_scroll_panel = new Panel();
	prec_scroll_panel.setSize(300, 20);
	prec_scroll.setValues(current_prec, 1, -100, 100);
	prec_scroll_panel.setLayout(new GridLayout(2,1));
	prec_scroll_panel.add(prec_scroll);
	Canvas c = new Canvas();
	c.setSize(300, 2);
	prec_scroll_panel.add(c);
	// prec_scroll.setSize(display_width, 20);
	prec_scroll.addAdjustmentListener(this);
	prec_window.setText(String.valueOf(current_prec));
	prec_window.setEditable(true);
	prec_window.addTextListener(this);
	prec_scroll.setValue(scroll_setting_from_prec(current_prec));

	add_button = new Button("+");
	add_button.setActionCommand("+");
	add_button.addActionListener(this);
	multiply_button = new Button("*");
	multiply_button.setActionCommand("*");
	multiply_button.addActionListener(this);
	subtract_button = new Button("-");
	subtract_button.setActionCommand("-");
	subtract_button.addActionListener(this);
	divide_button = new Button("/");
	divide_button.setActionCommand("/");
	divide_button.addActionListener(this);
	power_button = new Button("x^y");
	power_button.setActionCommand("^");
	power_button.addActionListener(this);
	enter_button = new Button("enter");
	enter_button.setActionCommand("$");
	enter_button.addActionListener(this);
	negate_button = new Button("+/-");
	negate_button.setActionCommand("~");
	negate_button.addActionListener(this);
	inverse_button = new Button("1/x");
	inverse_button.setActionCommand("%");
	inverse_button.addActionListener(this);
	exp_button = new Button("exp");
	exp_button.setActionCommand("x");
	exp_button.addActionListener(this);
	sqrt_button = new Button("sqrt");
	sqrt_button.setActionCommand("r");
	sqrt_button.addActionListener(this);
	ln_button = new Button("ln");
	ln_button.setActionCommand("l");
	ln_button.addActionListener(this);
	pi_button = new Button("pi");
	pi_button.setActionCommand("p");
	pi_button.addActionListener(this);
	dot_button = new Button(".");
	dot_button.setActionCommand(".");
	dot_button.addActionListener(this);
	dot_button.setFont(big_font);
	sin_button = new Button("sin");
	sin_button.setActionCommand("s");
	sin_button.addActionListener(this);
	cos_button = new Button("cos");
	cos_button.setActionCommand("k");
	cos_button.addActionListener(this);
	tan_button = new Button("tan");
	tan_button.setActionCommand("t");
	tan_button.addActionListener(this);
	asin_button = new Button("asin");
	asin_button.setActionCommand("S");
	asin_button.addActionListener(this);
	acos_button = new Button("acos");
	acos_button.setActionCommand("K");
	acos_button.addActionListener(this);
	atan_button = new Button("atan");
	atan_button.setActionCommand("T");
	atan_button.addActionListener(this);
	help_button = new Button("HELP!");
	help_button.addActionListener(this);
	help_button.setFont(medium_font);
	stop_button = new Button("stop");
	stop_button.addActionListener(this);
	stop_button.setFont(medium_font);
//	paste_button = new Button("paste cb");
//	paste_button.addActionListener(this);
	write_button = new Button("write");
	write_button.addActionListener(this);
	write_button.setActionCommand("w");
	write_button.setFont(medium_font);
	base_box = new Checkbox("base 16");
	base_box.addItemListener(this);
	base_box.setFont(medium_font);
	degree_box = new Checkbox("degrees");
	degree_box.addItemListener(this);
	degree_box.setFont(medium_font);
	replay_button = new Button("replay");
	replay_button.addActionListener(this);
	replay_button.setFont(medium_font);
	set_prec_button = new Button("set prec.");
	set_prec_button.addActionListener(this);
	set_prec_button.setFont(medium_font);
	set_prec_button.setActionCommand(">");
	xchg_button = new Button("xchg");
	xchg_button.setActionCommand("i");
	xchg_button.addActionListener(this);
	clear_button = new Button("C/CE");
	clear_button.setActionCommand("#");
	clear_button.addActionListener(this);
	clear_all_button = new Button("C All");
	clear_all_button.setActionCommand("@");
	clear_all_button.addActionListener(this);
	store_button = new Button("store M");
	store_button.setActionCommand("=");
	store_button.addActionListener(this);
	recall_button = new Button("get M");
	recall_button.setActionCommand("g");
	recall_button.addActionListener(this);
	copy_button = new Button("copy");
	copy_button.setActionCommand("q");
	copy_button.addActionListener(this);
	history = new TextArea("", 1 + extra_columns,
			       display_char_width,
		               TextArea.SCROLLBARS_HORIZONTAL_ONLY);
	control_panel.add(stop_button);
	control_panel.add(help_button);
	control_panel.add(set_prec_button);
	control_panel.add(replay_button);
	control_panel.add(write_button);
	control_panel.add(base_box);
	control_panel.add(degree_box);
	history.setEditable(true);
        for (int i = 0; i <= 9; ++i) {
	    String name = (new Integer(i)).toString();
	    digit_button[i] = new Button(name);
	    digit_button[i].setActionCommand(String.valueOf((char)('0' + i)));
	    digit_button[i].addActionListener(this);
	    digit_button[i].setFont(big_font);
	}
	// row 0
	keypad.add(inverse_button);
	keypad.add(sqrt_button);
	keypad.add(exp_button);
	keypad.add(ln_button);
	keypad.add(power_button);
	// row 1
	keypad.add(clear_button);
	keypad.add(sin_button);
	keypad.add(cos_button);
	keypad.add(tan_button);
	keypad.add(pi_button);
	// row 2
	keypad.add(clear_all_button);
	keypad.add(asin_button);
	keypad.add(acos_button);
	keypad.add(atan_button);
	keypad.add(divide_button);
        // row 3
	keypad.add(xchg_button);
	for (int i = 7; i <= 9; ++i) keypad.add(digit_button[i]);
	keypad.add(multiply_button);
	// row 4
	keypad.add(store_button);
	for (int i = 4; i <= 6; ++i) keypad.add(digit_button[i]);
	keypad.add(subtract_button);
	// row 5
	keypad.add(recall_button);
	for (int i = 1; i <= 3; ++i) keypad.add(digit_button[i]);
	keypad.add(add_button);
	// row 6
	keypad.add(copy_button);
	keypad.add(digit_button[0]);
	keypad.add(dot_button);
	keypad.add(negate_button);
	keypad.add(enter_button);

	cq = new command_queue();
	current_base = 10;
	current_big_base = big10;
	degree_mode = false;
	input_converter = output_converter = UnaryCRFunction.identityFunction;
	clear();

        please_stop = false;
	please_exit = false;
	worker = new Thread(this, "CRCalc worker");
	worker.setPriority(Thread.NORM_PRIORITY - 2);
	worker.start();
	ts = new time_slicer();
	ts_thread = new Thread(ts, "CRCalc time slicer");
	ts_thread.setPriority(Thread.MAX_PRIORITY);
	ts_thread.start();

	this.add(display);
	this.add(prec_scroll_panel);
	this.add(prec_window);
	keypanel.add(control_panel);
	keypanel.add(keypad);
	this.add(keypanel);
	this.add(history);
    }

    String display_font_name = null;

    boolean is_applet = true;

    // So we can also run this as an application:
    public static void main(String argv[]) {
	Frame f = new Frame("CR Calculator");
	CRCalc calc = new CRCalc();
  	calc.display_font_name = "Courier";
	calc.is_applet = false;
	calc.init();
	calc.start();
	f.add("Center", calc);
	f.setSize(500, 570);
	// calc.set_sizes();
	calc.validate();
	f.setVisible(true);
    }

    public void destroy() {
	cq.please_exit = true;
	ts.please_exit = true;
	please_exit = true;
	ts_thread.interrupt();
	worker.interrupt();
	// The following are probably unnecessary, but ...
	keypad.remove(add_button);
	keypad.remove(multiply_button);
	keypad.remove(subtract_button);
	keypad.remove(divide_button);
	keypad.remove(power_button);
	keypad.remove(enter_button);
	keypad.remove(negate_button);
	keypad.remove(inverse_button);
	keypad.remove(sqrt_button);
	keypad.remove(exp_button);
	keypad.remove(ln_button);
	keypad.remove(pi_button);
	keypad.remove(dot_button);
	keypad.remove(sin_button);
	keypad.remove(cos_button);
	keypad.remove(tan_button);
	keypad.remove(asin_button);
	keypad.remove(acos_button);
	keypad.remove(atan_button);
	keypad.remove(clear_button);
	keypad.remove(clear_all_button);
	keypad.remove(xchg_button);
	keypad.remove(copy_button);
	keypad.remove(store_button);
	keypad.remove(recall_button);
        for (int i = 0; i <= 9; ++i) {
	   keypad.remove(digit_button[i]);
	}
	control_panel.remove(help_button);
	control_panel.remove(stop_button);
	control_panel.remove(base_box);
	control_panel.remove(degree_box);
	control_panel.remove(set_prec_button);
	control_panel.remove(replay_button);
	control_panel.remove(write_button);
	remove(keypad);
	remove(display);
	remove(prec_scroll);
	remove(prec_window);
	remove(history);
	display_font_name = null;
    }

    Integer busy_lock= new Integer(0);
		// Used only as lock protecting busy flag.
    boolean busy = false;

    void set_busy() {
	synchronized(busy_lock) {
	    busy = true;
	    stop_button.setLabel("STOP!");
	}
    }

    void clear_busy() {
	synchronized(busy_lock) {
	    busy = false;
	    stop_button.setLabel("stop");
	}
    }

    volatile boolean please_stop;	// Request worker thread to stop.

    volatile boolean please_exit;	// Request helper threads to exit.

    // Most real actions need to be performed in a worker thread,
    // so that they can be interrupted.  This is the main procedure
    // for that thread.
    public void run() {
	char command;
	for (;;) {
	    if (please_exit) return;
	    command = cq.get();
	    try {
	      if (please_exit) return;
	      if (please_stop) continue;
	      set_busy();
	      if (Thread.interrupted()) throw new AbortedError();
	      if (!executeKeyCommand(command)) {
		warn("Bad command in command queue !?");
	      }
	    } catch (AbortedError e) {
	      warn("Aborted");
	      clear_entry();
	      add_history_string("<aborted>");
	      enqueueKeyCommand(command_queue.refresh_command);
	    } catch (ArithmeticException e) {
	      warn("Illegal operand");
	      clear_entry();
	      add_history_string("<error>");
	      enqueueKeyCommand(command_queue.refresh_command);
	    } catch (OutOfMemoryError e) {
	      clear_entry();
	      warn("Out of Memory");
	      add_history_string("<error>");
	      enqueueKeyCommand(command_queue.refresh_command);
	    } catch (PrecisionOverflowError e) {
	      warn("Overflow in requested precision value");
	      int prec = current_prec;
	      if (prec < -1000 || prec > 1000) {
	        current_prec = initial_prec;
		prec_changed(true);
	      } else {
		clear_entry();
	        add_history_string("<error>");
	        enqueueKeyCommand(command_queue.refresh_command);
	      } 
	    }
	    clear_busy();
	    CR.please_stop = false;
	}
    }

    void do_stop() {
	synchronized(busy_lock) {
	    if (busy) {
		worker.interrupt();
		CR.please_stop = true;
	    }
	}
    }

    void enqueueCommands(String commands) {
	int len = commands.length();
	for (int i = 0; i < len; ++i) {
	    enqueueKeyCommand(commands.charAt(i));
	}
    }

//  This doesn't really seem to be useful, given the extent of clipboard
//  support in browsers and JVMs.  It would be nice, but ...
//    String get_clipboard_data() {
//        try {
//    	    Clipboard cb = this.getToolkit().getSystemClipboard();
//    	    Transferable contents = cb.getContents(this);
//    	    String data = (String)
//			contents.getTransferData(DataFlavor.stringFlavor);
//    	    if (data.length() == 0) warn("No data in clipboard");
//    	    return data;
//        } catch (SecurityException e) {
//    	    warn("Applet is not allowed to retrieve clipboard");
//        } catch (UnsupportedFlavorException e) {
//    	    warn("Unsupported clipboard data format");
//        } catch (java.io.IOException e) {
//    	    warn("Error reading clipboard data");
//        }
//        return "";
//    }

    public void actionPerformed(ActionEvent e) {
	String command_string = e.getActionCommand();
	if (command_string.length() == 1) {
	    // It's the command to execute.
	    enqueueKeyCommand(command_string.charAt(0));
	} else {
	    Object source = e.getSource();
	    if (source == help_button) {
	        msg(help_text);
	    } else if (source == stop_button) {
	        do_stop();
	    } else if (source == replay_button) {
	        String commands = history.getSelectedText();
	        int len = commands.length();
	        if (len == 0) {
	    	    warn("No text selected in history buffer");
	        } else {
		    enqueueCommands(commands);
	        }
//	    } else if (source == paste_button) {
//	        String commands = get_clipboard_data();
//	        int len = commands.length();
//	        if (len != 0) {
//		    enqueueCommands(commands);
//	        }
	    }
	}
    }

    public void keyTyped(KeyEvent e) { e.consume(); }

    public void keyReleased(KeyEvent e) { e.consume(); }

    public void keyPressed(KeyEvent e) {
	char key = e.getKeyChar();
	if (enqueueKeyCommand(key)) e.consume();
    }

    public void itemStateChanged(ItemEvent e) {
	if (e.getItemSelectable() == base_box) {
	    if (base_box.getState() && 10 == current_base) {
		enqueueKeyCommand('!');
	    } else if (!base_box.getState() && 16 == current_base) {
		enqueueKeyCommand('!');
	    }
	} else if (e.getItemSelectable() == degree_box) {
	    if (degree_box.getState() && !degree_mode) {
		enqueueKeyCommand('\"');
	    } else if (!degree_box.getState() && degree_mode) {
		enqueueKeyCommand('\"');
	    }
	}
    }

    void wait_a_little() {
	try {
	    Thread.sleep(5000);
	} catch(InterruptedException e) {}
    }

    final int check_prec = -500;	// eval precision for arg checks.

    // Execute the command corresponding to a keyboard key.
    boolean executeKeyCommand(char key) {
	int signum;
	switch(key) {
	    case '0':
	    case '1':
	    case '2':
	    case '3':
	    case '4':
	    case '5':
	    case '6':
	    case '7':
	    case '8':
	    case '9':
	      add_digit(key - '0');
	      break;
	    case 'a':
	    case 'b':
	    case 'c':
	    case 'd':
	    case 'e':
	    case 'f':
	      add_digit(key - 'a' + 10);
	      break;
	    case 'A':
	    case 'B':
	    case 'C':
	    case 'D':
	    case 'E':
	    case 'F':
	      add_digit(key - 'A' + 10);
	      break;
	    case '$':
	    case ' ':
	    case '\r':
	    case '\n':
	      do_unary(UnaryCRFunction.identityFunction, '$');
	      break;
	    case '.':
	      add_period();
	      break;
	    case '+':
	      do_add();
	      break;
	    case '-':
	      do_subtract();
	      break;
	    case '*':
	      do_multiply();
	      break;
	    case '/':
	      if (!check_binary()) break;
	      if (stack[stack_ptr-1].signum(check_prec) == 0) {
		if (!ask("Probable division by 0.  Proceed?")) break;
	      }
	      do_divide();
	      break;
	    case '^':
	      if (!check_binary()) break;
	      signum = stack[stack_ptr-2].signum(check_prec);
	      if (signum < 0) {
		warn("Negative base not allowed");
		break;
	      }
	      if (signum == 0) {
		if (!ask("Base must be > 0, but appears to be 0.  Proceed?"))
		    break;
	      }
	      do_power();
	      break;
	    case '~':
	      do_unary(UnaryCRFunction.negateFunction, '~');
	      break;
	    case '%':
	      if (!check_unary()) break;
	      if (stack[stack_ptr-1].signum(check_prec) == 0) {
		if (!ask("Probable division by 0.  Proceed?")) break;
	      }
	      do_unary(UnaryCRFunction.inverseFunction, '%');
	      break;
	    case 'r':
	    case 'R':
	      if (!check_unary()) break;
	      if (stack[stack_ptr-1].signum(check_prec) < 0) {
		warn("Square root of negative number");
	      } else {
	        do_unary(UnaryCRFunction.sqrtFunction, 'r');
	      }
	      break;
	    case 'x':
	    case 'X':
	      do_unary(UnaryCRFunction.expFunction, 'x');
	      break;
	    case 'l':
	    case 'L':
	      if (!check_unary()) break;
	      signum = stack[stack_ptr-1].signum(check_prec);
	      if (signum < 0) {
		warn("log(negative number)");
		break;
	      }
	      if (signum == 0) {
		if (!ask("Probable log(0).  Proceed?")) break;
	      }
	      do_unary(UnaryCRFunction.lnFunction, 'l');
	      break;
	    case 'p':
	    case 'P':
	      do_pi();
	      break;
	    case 's':
	      do_unary(UnaryCRFunction.sinFunction.compose(input_converter),
		       's');
	      break;
	    case 'k':
	      do_unary(UnaryCRFunction.cosFunction.compose(input_converter),
		       'k');
	      break;
	    case 't':
	      if (!check_unary()) break;
	      // Check for an argument that is an odd multiple of PI/2
	      // Do this in stages, to make it cheaper.
	      {
		CR top = stack[stack_ptr-1];
		CR half = CR.valueOf(1).shiftRight(1);
		CR pi_mul = top.divide(CR.PI).subtract(half);
		int initial_check_prec = check_prec/10;
		BigInteger pi_mul_appr = pi_mul.get_appr(initial_check_prec);
		if (pi_mul_appr.shiftRight(-initial_check_prec)
			       .shiftLeft(-initial_check_prec)
			       .equals(pi_mul_appr)) {
		  pi_mul_appr = pi_mul.get_appr(check_prec);
		  if (pi_mul_appr.shiftRight(-check_prec)
			         .shiftLeft(-check_prec)
				 .equals(pi_mul_appr)) {
		    if (!ask("Probable diverging tan().  Proceed?")) break;
		  }
		}
	      }
		
	      do_unary(UnaryCRFunction.tanFunction.compose(input_converter),
		       't');
	      break;
	    case 'S':
	      do_unary(output_converter.compose(UnaryCRFunction.asinFunction),
		       'S');
	      break;
	    case 'K':
	      do_unary(output_converter.compose(UnaryCRFunction.acosFunction),
		       'K');
	      break;
	    case 'T':
	      do_unary(output_converter.compose(UnaryCRFunction.atanFunction),
		       'T');
	      break;
    	    case 'w':
	    case 'W':   // write
	      if (!check_unary()) break;
	      int convert_prec = current_prec >= 0? 0 : -current_prec;
	      String tos = stack[stack_ptr-1].toString(convert_prec
						       + extra_prec,
						       current_base);
	      int len = tos.length();
	      System.out.println(tos.substring(0, len-extra_prec));
	      break;
	    case '>':   // set precision
	      add_history_char('>');
	      if (!check_unary()) break;
	      current_prec = stack[--stack_ptr].intValue();
	      prec_changed(true);
	      break;
	    case 'q':
	    case 'Q':
	      do_copy();
	      break;
	    case 'i':
	    case 'I':
	      do_exchange();
	      break;
	    case '=':
	      do_store();
	      break;
	    case 'g':
	      do_recall();
	      break;
	    case '\b':
	      if (digits_entered > 0 || point_entered) {
		delete_last_char();
		refresh();
		break;
	      } // else fall through and clear entry;
	    case '#':
	      if (digits_entered == 0) add_history_char('#');
	      clear_entry();
	      refresh();
	      break;
	    case '@':
	      add_history_char('@');
	      clear();
	      refresh();
	      break;
	    case command_queue.refresh_command:
	      // internal use only
	      refresh();
	      break;
	    case '!':
	      add_history_char('!');
	      if (current_base == 10) {
		current_base = 16;
		current_big_base = big16;
		if (!base_box.getState()) base_box.setState(true);
	      } else {
		current_base = 10;
		current_big_base = big10;
		if (base_box.getState()) base_box.setState(false);
	      }
	      refresh();
	      break;
	    case '\"':
	      add_history_char('\"');
	      if (degree_mode) {
		degree_mode = false;
		input_converter = UnaryCRFunction.identityFunction;
		output_converter = UnaryCRFunction.identityFunction;
		if (degree_box.getState()) degree_box.setState(false);
	      } else {
		degree_mode = true;
		input_converter = from_degrees;
		output_converter = to_degrees;
		if (!degree_box.getState()) degree_box.setState(true);
	      }
	      refresh();
	      break;
	    default:
	      return false;
	}
	return true;
    }

    // Enqueue the command corresponding to a keyboard key.
    // Return true if it is a valid command.
    boolean enqueueKeyCommand(char key) {
	switch(key) {
	    case '0':
	    case '1':
	    case '2':
	    case '3':
	    case '4':
	    case '5':
	    case '6':
	    case '7':
	    case '8':
	    case '9':
	    case '$':
	    case ' ':
	    case '\r':
	    case '\n':
	    case '.':
	    case '+':
	    case '-':
	    case '*':
	    case '/':
	    case '^':
	    case '~':
	    case '%':
	    case 'r':
	    case 'R':
	    case 'x':
	    case 'X':
	    case 'l':
	    case 'L':
	    case 'p':
	    case 'P':
	    case 's':
	    case 'k':
	    case 't':
	    case 'S':
	    case 'K':
	    case 'T':
	    case 'q':
	    case 'Q':
	    case 'i':
	    case 'I':
	    case '=':
	    case 'g':
	    case '#':
	    case '\b':
	    case '@':
	    case '!':
	    case '\"':
	    case 'w':
	    case 'W':
	    case '>':
	    case command_queue.refresh_command:
	      cq.add(key);
	      return true;
	    case 'a':
	    case 'b':
	    case 'c':
	    case 'd':
	    case 'e':
	    case 'f':
	    case 'A':
	    case 'B':
	    case 'C':
	    case 'D':
	    case 'E':
	    case 'F':
	      if (current_base == 16) {
		cq.add(key);
		return true;
	      } else {
		return false;
	      }
	    default:
	      return false;
	}
    }


    public String getAppletInfo() {
	return "SGI Calculator for demand evaluated real numbers V 0.4.\n"
	        + "Author: Hans-J. Boehm\n"
		+ "Copyright © 1999, Silicon Graphics, Inc. -- ALL RIGHTS RESERVED\n"
		+ "See COPYRIGHT.txt for details.\n";
    }
    
    private String[][] param_info = {
	{"font", "font family name", "font for display window"},
	{"rows", "decimal integer", "number of rows in display"},
	{"extra_rows", "decimal integer",
	 "rows of padding in display and history"},
	{"extra_columns", "decimal integer",
	 "padding columns in display"},
    };

    public String[][] getParameterInfo() { return param_info; }
    
}
