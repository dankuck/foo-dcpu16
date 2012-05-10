; Calculate the factorial of a 16-bit integer
;
; Author: while1dan

.ifndef factorial

:factorial ; (A = the number to factorialize, result in A)
	SET B, A
	IFE B, 1
		SET PC, _done
	:_loop
	SUB B, 1
	IFE B, 1
		SET PC, _done
	MUL A, B
	IFN O, 0
		SET PC, _error_out
	SET PC, _loop
	
	:_done
	SET PC, POP
	
	:_error_out
	SET A, 0
	SET PC, _done

.end
