;Submitted by ViEtArmis.
;Accessed from http://0x10c-programs.com/view/?id=30

;Some changes have been applied for library compatibility and to conform to standards group stuff

.ifndef sqrt32

:sqrt32
	; pass in via BA (B is the big half), returns A
	SET PUSH, X
	SET PUSH, Y
	SET PUSH, Z
	SET PUSH, I
	SET PUSH, J

	; clear registers
	SET C, 0
	SET X, 16384
	SET Y, 0
	SET Z, 0
	SET I, 0
	SET J, 0

	IFG X, B
	    SET PC, _sqrt_while1
	SET PC, _sqrt_next1

	; set XC to highest power of 4 < BA
	:_sqrt_while1
	SHR X, 2
	SET C, O
	IFE X, 0 ; if it overflows to lower half, move on
	    SET PC, _sqrt_while2
	IFG X, B
	    SET PC, _sqrt_while1
	SET PC, _sqrt_next1

	:_sqrt_while2
	IFG A, C
	    SET PC, _sqrt_next1
	:_sqrt_while2a
	SHR C, 2
	IFG C, A
	    SET PC, _sqrt_while2a
	IFE C, A
	    SET PC, _sqrt_while2a

	:_sqrt_next1
	SET Z, 0
	IFE X, 0
	    JSR _check_xc1
	IFE Z, 1
	    SET PC, _sqrt_done

	; turn JI into JI+XC
	ADD I, C
	ADD J, O
	ADD J, X

	SET Z, 0

	; check if BA >= JI
	JSR _check_ji1

	; turn it back
	SUB I, C
	ADD J, O
	SUB J, X

	; perform else contents
	IFE Z, 1
	    SET PC, _sqrt_cont1

	; JI = JI >> 1
	SHR I, 1
	SHR J, 1
	XOR I, O

	; XC = XC >> 2
	SHR C, 2
	SHR X, 2
	XOR C, O

	; loop
	SET PC, _sqrt_next1

	:_check_xc1
	IFE C, 0
	    SET Z, 1
	SET PC, POP

	:_check_ji1
	IFG B, J
	    SET Z, 1
	IFE B, J
	    JSR _check_ji2
	SET PC, POP

	:_check_ji2
	IFG A, I
	    SET Z, 1
	IFE A, I
	    SET Z, 1
	SET PC, POP

	; if contents loop
	:_sqrt_cont1
	; BA -= XC + JI
	SUB A, I
	ADD B, O
	SUB B, J
	SUB A, C
	ADD B, O
	SUB B, X

	; JI = JI >> 1 + XC
	SHR I, 1
	SHR J, 1
	XOR I, O
	ADD I, C
	ADD J, O
	ADD J, X

	; XC = XC >> 2
	SHR C, 2
	SHR X, 2
	XOR C, O

	; loop
	SET PC, _sqrt_next1

	; return
	:_sqrt_done

	SET A, I
	SET J, POP 
	SET I, POP
	SET Z, POP
	SET Y, POP
	SET X, POP
	SET PC, POP

.end
