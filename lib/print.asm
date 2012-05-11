; Put text into the display portion of memory
; print               takes a p-string (ie, the kind with the length in the first word)
; printc              takes a c-string (ie, the kind with a 0 as the last word)
; print_number        takes an unsigned number
; print_signed_number takes a twos-complement signed number
; scroll_screen       moves the data currently in the display up one line, 
;                     ignoring the top line and clearing the new last line
;
; Author: while1dan


.ifndef print

.def display_start           0x8000
.def display_length          0x180
.def display_end             display_start+display_length
.def display_last_line_start display_start+0x160

:cursor_position
DAT display_start

;prints a number prefixed string
:print	; (A = string pointer)
	SET PUSH, X
	
	SET B, [cursor_position]
	SET C, [A]
	ADD A, 1

	:_loop
	IFE C, 0
		SET PC, _done
	IFE [A], 0xA ; \n
		SET PC, _print_newline
	:_print_char_literally
	SET [B], [A]
	ADD B, 1
	JSR _check_scroll_screen
	:_done_char_print
	ADD A, 1
	SUB C, 1
	SET PC, _loop
	
	:_done
	SET [cursor_position], B
	SET X, POP
	SET PC, POP
	
	:_print_newline
	SET [B], 0
	ADD B, 1
	JSR _check_scroll_screen
	SET X, B
	MOD X, 0x20 ; 32 chars on line
	IFE X, 0
		SET PC, _done_char_print
	SET PC, _print_newline
	
	:_check_scroll_screen
	IFN B, display_end
		SET PC, POP
	SET PUSH, A
	SET PUSH, C
	JSR scroll_screen
	SET C, POP
	SET A, POP
	SET B, display_last_line_start
	SET PC, POP

;print a null-char delimited string
:printc	; (A = string pointer)

	SET B, [cursor_position]

	:_loop
	SET C, [A]
	IFE C, 0
		SET PC, _endprint
	IFE C, 0xA ; \n
		SET PC, _print_newline
	:_print_char_literally
	SET [B], C
	ADD B, 1
	JSR _check_scroll_screen
	:_done_char_print
	ADD A, 1
	SET PC, _loop
	
	:_endprint
	SET [cursor_position], B
	SET PC, POP
	
	:_print_newline
	SET [B], 0
	ADD B, 1
	JSR _check_scroll_screen
	SET C, B
	MOD C, 0x20 ; 32 chars on line
	IFE C, 0
		SET PC, _done_char_print
	SET PC, _print_newline
	

	:_check_scroll_screen
	IFN B, display_end
		SET PC, POP
	SET PUSH, A
	SET PUSH, C
	JSR scroll_screen
	SET C, POP
	SET A, POP
	SET B, display_last_line_start
	SET PC, POP
	
:scroll_screen
	SET A, display_start
	:_scroll_loop
	SET [A], [32+A]
	ADD A, 1
	IFN A, display_last_line_start
		SET PC, _scroll_loop
	:_clear_bottom_line_loop
	SET [A], 0
	ADD A, 1
	IFN A, display_end
		SET PC, _clear_bottom_line_loop
	SET PC, POP

:print_number ; (A = number to print)
	SET [_the_number], 5
	SET B, A
	DIV B, 10000
	SET [_the_number+1], [_digits+B]
	MOD A, 10000
	
	SET B, A
	DIV B, 1000
	SET [_the_number+2], [_digits+B]
	MOD A, 1000
	
	SET B, A
	DIV B, 100
	SET [_the_number+3], [_digits+B]
	MOD A, 100

	SET B, A
	DIV B, 10
	SET [_the_number+4], [_digits+B]
	MOD A, 10

	SET [_the_number+5], [_digits+A]
	:_done
	SET A, _the_number
	SET PC, print

	:_digits
	DAT "0123456789"

	:_the_number
	.fill 6, 0
	
:print_signed_number ; (A = number to print)
	SET PUSH, B
	SET B, A
	AND B, display_start
	IFE B, 0
		SET PC, _print_as_if_unsigned
	SET PUSH, A
	SET A, _negative_string
	JSR print
	SET A, POP
	XOR A, 0xFFFF; A=~A
	ADD A, 1
	
	:_print_as_if_unsigned
	SET B, POP
	SET PC, print_number
	
	:_negative_string
	.ascii p"-"

:println
	JSR print
	SET A, _newline
	JSR print
	SET PC, POP
	
	:_newline
	.dw p"\n"	
.end
