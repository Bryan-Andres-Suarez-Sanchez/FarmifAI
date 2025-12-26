# Reporte de Evaluación de Similitud Semántica
## AgroChat - 2025-12-26 10:01:30

---

## Resumen Ejecutivo

| Método | Top-1 Accuracy | Top-3 Accuracy | Top-5 Accuracy |
|--------|----------------|----------------|----------------|
| **Pooler Output** | 83.3% | 94.4% | 100.0% |
| **Mean Pooling** | 94.4% | 97.2% | 100.0% |

**Mejor método:** Mean Pooling

---

## Detalles por Método

### 1. Pooler Output

- Total de pruebas: 36
- Correctos en Top-1: 30
- Correctos en Top-3: 34
- Correctos en Top-5: 36

#### Fallos (6):

| Query | Score | Obtuvo |
|-------|-------|--------|
| trips en cultivos | 0.9400 | Guía completa cultivo durazno |
| cuándo fertilizar | 0.7306 | ¿Cuándo sembrar frijol? |
| deficiencia de nitrógeno | 0.5772 | Eficiencia de riego |
| distancia entre plantas | 0.7819 | Macronutrientes plantas |
| preparar terreno para cultivo | 0.8737 | Maíz listo para cosecha |
| qué puedes hacer | 0.9569 | ¿Qué puedes hacer? |


### 2. Mean Pooling

- Total de pruebas: 36
- Correctos en Top-1: 34
- Correctos en Top-3: 35
- Correctos en Top-5: 36

#### Fallos (2):

| Query | Score | Obtuvo |
|-------|-------|--------|
| cuándo fertilizar | 0.6203 | ¿Cuándo sembrar frijol? |
| qué puedes hacer | 0.8977 | ¿Qué puedes hacer? |


---

## Análisis Detallado de Casos Problemáticos

### Casos donde AMBOS métodos fallan:

- `qué puedes hacer`
- `cuándo fertilizar`

### Casos donde Mean Pooling es MEJOR:

- `preparar terreno para cultivo`
- `deficiencia de nitrógeno`
- `distancia entre plantas`
- `trips en cultivos`


---

## Recomendaciones


1. **Usar Mean Pooling** - Tiene 11.1% mejor precisión en Top-1
2. Regenerar `kb_embeddings.npy` usando mean pooling
3. Asegurar que MindSpore en Android use `last_hidden_state` con mean pooling


## Configuración del Test

- Modelo: `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`
- Dimensión embeddings: 384
- Secuencia máxima: 128
- Total casos de prueba: 36
- KB Path: `/home/asus/Escritorio/proyectos/Huawei/AgroChat_Project/app/src/main/assets/agrochat_knowledge_base.json`
- Embeddings Path: `/home/asus/Escritorio/proyectos/Huawei/AgroChat_Project/app/src/main/assets/kb_embeddings.npy`

---

*Reporte generado automáticamente*
