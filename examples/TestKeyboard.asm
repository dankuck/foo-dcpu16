; Gets keyboard input and displays it.

:start
	SET X, boot_up_message
	JSR print
:main_loop
	JSR blink
	JSR getkey
	IFE Y, 0
		SET PC, main_loop
	IFE Y, 8
		SET PC, backspace
	SET [char_printer_string], Y
	SET X, char_printer_string
	JSR print
	SET PC, main_loop
	
	:backspace
	SUB [cursor_position], 1
	SET PC, main_loop
	
	:char_printer_string
	DAT 0x0, 0x0 ; a place to put a char so that it can be treated as a c-string
	
	:boot_up_message
	DAT "       DCPU16\n\nReady.\n\0"
	
:getkey ; reads a word from the keyboard buffer into Y
	SET I, [keyboard_position]
	SET Y, [I]
	IFE Y, 0
		SET PC, POP
	SET [I], 0
	ADD [keyboard_position], 1
	AND [keyboard_position], 0x900F
	SET PC, POP
	
	:keyboard_position
	DAT 0x9000

:blink
	ADD [blink_timer], 1
	AND [blink_timer], 0xFF
	IFN [blink_timer], 0
		SET PC, POP
	IFE [blink_string], [blink_underscore]
		SET PC, unset_blink
	SET [blink_string], [blink_underscore]
	SET PC, show_blink
	:unset_blink
	SET [blink_string], 0x20
	:show_blink
	SET X, blink_string
	JSR print
	SUB [cursor_position], 1
	SET PC, POP
	
	:blink_timer
	DAT 0x0000
	
	:blink_underscore
	DAT "_"
	
	:blink_string
	DAT "_", 0x0
	
:print	; (X = string pointer)

	SET B, [cursor_position]

	:printloop
	SET A, [X]
	IFE A, 0
		SET PC, endprint
	IFE A, 0xA ; \n
		SET PC, print_newline
	:print_char_literally
	SET [B], A
	ADD B, 1
	JSR check_scroll_screen
	:done_char_print
	ADD X, 1
	SET PC, printloop
	:endprint
	SET [cursor_position], B
	SET PC, POP
	
	:print_newline
	SET [B], 0
	ADD B, 1
	JSR check_scroll_screen
	SET A, B
	MOD A, 0x20 ; 32 chars on line
	IFE A, 0
		SET PC, done_char_print
	SET PC, print_newline
	
	:cursor_position
	DAT 0x8000

	:check_scroll_screen
		IFE B, 0x8180
			JSR scroll_screen
		SET PC, POP
	
	:scroll_screen
		SET B, 0x8160
		SET I, 0x8000
		:scroll_screen_loop
		SET [I], [32+I]
		ADD I, 1
		IFE I, 0x8160
			SET PC, clear_bottom_line_loop
		SET PC, scroll_screen_loop
		:clear_bottom_line_loop
		SET [I], 0
		ADD I, 1
		IFE I, 0x8180
			SET PC, POP
		SET PC, clear_bottom_line_loop
	
:crash
	DAT 0x0000 ; causes chip to actually crash
	; SET PC, crash ; Notch's favorite crash is to go into an infinite loop