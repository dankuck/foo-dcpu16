; Writes a string to the screen in a loop, 
; scrolling when appropriate and pausing 
; 1/2 second between prints

:start	
:main_loop
	SET X, string_to_print
	JSR print
	SET X, 500
	JSR pause
	SET PC, main_loop

:pause	; (X = milliseconds to waste [assuming 100 kHz DCPU]) Max = 4000 ms = 4 seconds
	SET I, 0
	MUL X, 16 ; X ms * 100 c/ms * 1 loop/16 c = the loops we need to run
	IFN O, 0  ; If the overflow register is not 0, then X was too high
		SET PC, pause_too_high
	:pause_loop
	ADD I, 1
	IFN I, X
		SET PC, pause_loop
	SET PC, POP
	:pause_too_high ; echo an error message and crash
		SET I, pause_too_high_message
		SET J, 0x8000
		:pause_too_high_loop
		IFE [I], 0
			SET PC, pause_crash
		SET [J], [I]
		ADD I, 1
		ADD J, 1
		SET PC, pause_too_high_loop
		:pause_too_high_message
		DAT "================================ Call to pause with X too high,  maxes out at 4000 milliseconds ================================", 0x0
		:pause_crash
		; DAT 0x0000  ; when running in a program without its own crash, uncomment this line
		SET PC, crash ; when running in a program with a crash, uncomment this line
	
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

:string_to_print
	DAT "Holy crap it worked! ", 0x0
