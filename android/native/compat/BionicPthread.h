#pragma once

#include <pthread.h>

#ifndef PTHREAD_PRIO_INHERIT
#define PTHREAD_PRIO_INHERIT 1
#endif

static inline int pthread_mutexattr_setprotocol (pthread_mutexattr_t*, int)
{
    return 0;
}
