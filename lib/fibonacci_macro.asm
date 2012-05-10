; Calculates a fibonacci number via a macro
; This is certainly the wrong way to do this
; But it's a good example of a recursive macro
;
; Author: while1dan

.ifndef fib_space{
.def fib_space 0x1000
}

.macro fibonacci(number){
	.if number>1
		fibonacci(number-1);
	.end
	.if number<3
		SET [fib_space+number], 1
	.else
		SET [fib_space+number], [fib_space+number-1]
		ADD [fib_space+number], [fib_space+number-2]
	.end
}

