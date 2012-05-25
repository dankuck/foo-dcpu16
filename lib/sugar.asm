; Syntactic sugar
;
; Would you rather say pop(X) than SET X, POP?
; This is what you want.
;
; Author: while1dan


.ifndef pop_macro
.def pop_macro 1
.macro pop(target){
	SET target, POP
}
.end


.ifndef push_macro
.def push_macro 1
.macro push(target){
	SET PUSH, target
}
.end


.ifndef peek_macro
.def peek_macro 1
.macro peek(target){
	SET target, [SP]
}
.end


.ifndef return_macro
.def return_macro 1
.macro return(){
	SET PC, POP
}
.end


.ifndef jmp_macro
.def jmp_macro 1
.macro jmp(target){
	SET PC, target
}
.end
