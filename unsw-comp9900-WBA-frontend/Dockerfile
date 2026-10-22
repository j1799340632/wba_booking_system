# The first stage: Node compilation (Breaking updates are typically performed between major Node.js versions, so remember to test before making any changes)
FROM node:24.18.1 AS builder
WORKDIR /app
# Copy package.json and source code
COPY package*.json ./
RUN npm install
COPY . .
# Perform a Vite build
RUN npm run build

# The second stage: Nginx Deployment
FROM nginx:latest
# Copy the built static files to the Nginx directory
COPY --from=builder /app/dist /usr/share/nginx/html
# Replace the default Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]