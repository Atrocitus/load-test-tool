package com.airwatch.tool;

import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.http.HttpClientResponse;
import rx.Observable;
import rx.Subscriber;

public final class RxSupport {

    private RxSupport() {
        // don't construct
    }

    public static Observable<Buffer> observeBody(final HttpClientResponse response) {
        Buffer body = Buffer.buffer();
        return Observable.create((Subscriber<? super Buffer> subscriber) -> {
            response.toObservable().subscribe(body::appendBuffer,
                    subscriber::onError,
                    () -> {
                        subscriber.onNext(body);
                        subscriber.onCompleted();
                    });
        });
    }
}