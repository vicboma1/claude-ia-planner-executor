# project-planner-executor (Opción A)

Arquitectura **Planner / Executor split**, minimal y explícita.

Flujo:
SPEC → Planner → Executor → Verifier

Características:
- Separación estricta de responsabilidades
- SPECS como fuente de verdad
- Alta fiabilidad con bajo coste cognitivo

Ideal para:
- features medianas
- refactors controlados
- Claude Code workflows


 Por qué este diseño funciona TAN bien en Claude Code

✅ Reduce al mínimo las alucinaciones

✅ El Planner puede ser “inteligente” sin riesgo

✅ El Executor es puramente mecánico

✅ El Verifier corta errores pronto

✅ Fácil de evolucionar a: Planner → Reviewer → Executor → Verifier
