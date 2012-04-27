; Test writing a string from data to the screen memory

:start	SET X, string_to_print
	JSR print
	SET PC, start

:print	; (X = string pointer)

	SET B, [cursor_position]

:printloop
	SET A, [X]
	IFE A, 0
		SET PC, endprint
	SET [B], A
	ADD X, 1
	ADD B, 1
	IFE B, 0x8180
		JSR scroll_screen
	SET PC, printloop
:endprint
	SET [cursor_position], B
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

:string_to_print
	DAT "Holy crap it worked! ", 0x0

:cursor_position
	DAT 0x8000