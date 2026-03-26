1. Búsqueda densa con otra métrica

Es casi lo mismo que ya mencionaste, pero cambiando la forma de medir cercanía entre embeddings.

Producto punto (dot product)
Distancia euclidiana
Maximum inner product search (MIPS)

La idea no cambia: consulta y chunks se representan como vectores y se buscan los más cercanos en el espacio semántico.

2. Bi-encoder retrieval

Este realmente es el esquema típico de embeddings.

Un encoder transforma la consulta en vector.
El mismo modelo, o uno compatible, transforma los documentos en vectores.
Luego comparas ambos.

Se parece muchísimo a lo que ya tienes. De hecho, muchas veces “coseno sobre embeddings” es justamente un bi-encoder retriever.

3. Dual encoder entrenado para recuperación

Muy parecido al bi-encoder, pero más especializado.

Un encoder para consultas
Otro para documentos
Ambos se entrenan para acercar pares relevantes y alejar pares irrelevantes

Esto suele funcionar mejor que usar embeddings genéricos, porque el modelo aprende específicamente la tarea de recuperar.

4. Recuperación por vecinos cercanos aproximados (ANN)

Aquí no cambia la lógica semántica, sino la forma de buscar más rápido.

Ejemplos:

FAISS
HNSW
Annoy
ScaNN

Sigues haciendo búsqueda semántica por embeddings, pero con índices eficientes. En la práctica, muchos RAG reales usan esto.

5. Late interaction / multi-vector retrieval

Aquí ya no representas todo el chunk con un solo vector.

Ejemplo famoso:

ColBERT

Qué hace:

guarda varios vectores por documento o por tokens
compara consulta y documento con interacción más fina

Ventaja:

captura mejor detalles semánticos que un solo embedding global

Es menos “literal” que BM25, pero más preciso que un embedding único en muchos casos.

6. Recuperación neural con reranker semántico

No es exactamente el mismo paso, pero se parece en objetivo: usar significado, no palabras.

Flujo:

recuperas candidatos semánticamente
un cross-encoder los vuelve a evaluar uno por uno

Ejemplos:

BERT reranker
cross-encoder de sentence-transformers

Ventaja:

entiende mejor la relación consulta-documento
suele mejorar mucho la relevancia final
7. Recuperación basada en expansión semántica de consulta

Aquí la consulta se transforma antes de buscar.

Ejemplos:

generar una versión más clara de la consulta
generar varias consultas equivalentes
usar sinónimos o reformulación con LLM

Por ejemplo:
“mi café tiene hojas naranjas”
se puede expandir a:
“roya del café”, “hongo en hojas de café”, “control de roya”

No depende solo de coincidencia literal, porque agregas semántica antes de recuperar.

8. HyDE

Método interesante para RAG.

Primero el modelo genera una respuesta hipotética al prompt.
Luego haces embedding de esa respuesta hipotética.
Recuperas documentos cercanos a ese embedding.

Esto ayuda cuando la pregunta del usuario es corta, ambigua o poco técnica.

9. Retrieval con knowledge graph o enlaces conceptuales

Aquí la relación no es literal sino conceptual.

Ejemplo:

“roya” se conecta con “hongo”, “Hemileia vastatrix”, “fungicida”, “cafeto”

No depende de coincidencia exacta, sino de relaciones estructuradas entre conceptos. Es útil, pero más costoso de construir.

10. Topic retrieval / semantic clustering

En vez de recuperar por palabras, recuperas por temas o clusters semánticos.

Ejemplo:

cluster de “enfermedades del café”
cluster de “fertilización”
cluster de “riego”

La consulta se asigna a uno o varios temas y luego se busca dentro de ellos.

No suele ser suficiente por sí solo, pero sirve como apoyo.