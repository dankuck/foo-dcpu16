; Gets keyboard input and displays it.
SET PC, start

.include "lib/print.asm"
.include "lib/crash.asm"
.include "lib/nextkey.asm"

:start
	SET A, boot_up_message
	JSR print
:main_loop
	JSR blink
	JSR nextkey
	IFE A, 0
		SET PC, main_loop
	IFE A, 8
		SET PC, _backspace
	SET [_char_printer_string+1], A
	SET A, _char_printer_string
	JSR print
	SET PC, main_loop
	
	:_backspace
	SUB [cursor_position], 1
	SET PC, main_loop
	
	:_char_printer_string
	DAT p" " ; a place to put a char so that it can be treated as a c-string

:boot_up_message
DAT p"       DCPU16\n\nReady.\n"
	
:blink
	ADD [_blink_timer], 1
	AND [_blink_timer], 0xFF
	IFN [_blink_timer], 0
		SET PC, POP
	IFE [_blink_string + 1], [_blink_underscore]
		SET PC, _unset_blink
	SET [_blink_string + 1], [_blink_underscore]
	SET PC, _show_blink
	:_unset_blink
	SET [_blink_string + 1], 0x20
	:_show_blink
	SET A, _blink_string
	JSR print
	SUB [cursor_position], 1
	SET PC, POP
	
	:_blink_timer
	DAT 0x0000
	
	:_blink_underscore
	DAT "_"
	
	:_blink_string
	DAT p"_"
	
