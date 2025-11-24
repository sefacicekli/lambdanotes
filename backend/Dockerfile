FROM golang:1.21-alpine

WORKDIR /app

# Git'i yükle (Alpine için)
RUN apk add --no-cache git

COPY go.mod ./
# COPY go.sum ./ # Eğer varsa

COPY . .

RUN go build -o main .

EXPOSE 3286

CMD ["./main"]
