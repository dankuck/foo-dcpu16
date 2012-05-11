; Loop for a given number of milliseconds
; This assumes that your DCPU16 is running at 100 kHz. 
; Which it is of course.
;
; Author: while1dan

.ifndef pause

:pause	; (A = milliseconds to waste [assuming 100 kHz DCPU]) Max = 4000 ms = 4 seconds
	SET B, 0
	MUL A, 16 ; X ms * 100 cycles/ms * 1 loop/16 cycles = the loops we need to run
	IFN O, 0  ; If the overflow register is not 0, then A was too high
		SET PC, pause_too_high
	:_loop
	ADD B, 1
	IFN B, A
		SET PC, _loop
	SET PC, POP
	
:pause_too_high ; echo an error message and crash
	.ifdef print
		SET A, _message
		JSR print
		SET PC, _crash
	.else
		SET B, _message+1
		SET C, 0x8000
		:_loop
		IFE [B], 0
			SET PC, _crash
		SET [C], [B]
		ADD B, 1
		ADD C, 1
		SET PC, _loop
	.end
	
	:_message
	DAT p"Call to pause with A too high,  maxes out at 4000 milliseconds", 0x0
	
	:_crash
	.ifdef crash
		SET PC, crash
	.else
		DAT 0x0000
	.end

.end
