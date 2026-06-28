package org.linkweave.api.types.id;

import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class EntityClassNameParser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1964409785023570224L;

    @SuppressWarnings("rawtypes")
    private final Class<?> entityClass;

    public Class<?> entityClassFrom(Type type) {
        var idType = validParameterizedIDType(type);

        var entityTypeParam = validEntityTypeParam(idType);

        return entityTypeParam;
    }

    private Class<?> validEntityTypeParam(ParameterizedType idType) {
        var entityParamType = idType.getActualTypeArguments()[0];

        if (ParameterizedType.class.isAssignableFrom(entityParamType.getClass())) {
            return (Class<?>) ((ParameterizedType) entityParamType).getRawType();
        }

        if (Class.class.isAssignableFrom(entityParamType.getClass())) {
            return (Class<?>) entityParamType;
        }

        throw entityParamTypeNotImplemented(entityParamType);
    }

    private ParameterizedType validParameterizedIDType(Type type) {
        if (ParameterizedType.class.isAssignableFrom(type.getClass())) {
            var parameterizedType = (ParameterizedType) type;

            if (entityClass.isAssignableFrom((Class<?>) parameterizedType.getRawType())) {
                return parameterizedType;
            }

            throw illegalType(type);
        }

        if (entityClass.isAssignableFrom((Class<?>) type)) {
            throw rawType();
        }

        throw illegalType(type);

    }

    private IllegalArgumentException entityParamTypeNotImplemented(Type entityParamType) {
        return new IllegalArgumentException("Not yet implemented: %s = %s".formatted(
            entityParamType.getClass(), entityParamType
        ));
    }

    private IllegalArgumentException rawType() {
        return new IllegalArgumentException("%s required with generic type arguments but only got raw type".formatted(
            entityClass.getName()
        ));
    }

    private IllegalArgumentException illegalType(Type type) {
        return new IllegalArgumentException("Can only parse class %s<?> but got: %s".formatted(
            entityClass.getName(), type.getTypeName()
        ));
    }

}
