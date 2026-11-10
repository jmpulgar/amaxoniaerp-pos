-- Normalización de tipo de movimiento 14 para devoluciones en kardex
UPDATE tipo_movimiento_almacen 
SET descripcion = 'Devolución (Ventas)', operacion = '+' 
WHERE id_tipo_movimiento_almacen = 14;
